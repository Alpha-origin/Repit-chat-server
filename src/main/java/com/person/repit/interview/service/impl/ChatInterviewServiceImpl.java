package com.person.repit.interview.service.impl;

import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.dto.request.ChatInterviewResultSaveRequest;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.*;
import com.person.repit.interview.exception.*;
import com.person.repit.interview.model.ChatAnswer;
import com.person.repit.interview.model.ChatInterviewSession;
import com.person.repit.interview.model.ChatQuestion;
import com.person.repit.interview.service.AiQuestionClient;
import com.person.repit.interview.service.ApiServerClient;
import com.person.repit.interview.service.ChatInterviewService;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.QuestionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatInterviewServiceImpl implements ChatInterviewService {

    private static final String KEY_PREFIX = "chat:interview:";
    private static final Duration SESSION_TTL = Duration.ofHours(3);
    private static final int ORIGINAL_QUESTIONS_PER_FOLLOW = 2;


    private final AiQuestionClient aiQuestionClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApiServerClient apiServerClient;


    @Override
    @Transactional
    public ChatInterviewResponse prepareInterview(ChatInterviewPrepareRequest request, String authorization) {
        log.info("jobId={}", request.getJobId());

        log.info(
                "sessionId={}, interviewId={}, jobId={}",
                request.getSessionId(),
                request.getInterviewId(),
                request.getJobId()
        );

        String key = createKey(request.getSessionId());

        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new InterviewSessionAlreadyExistsException("이미 존재하는 세션입니다.");
        }

        MockInterviewResponse mockInterview =
                apiServerClient.getMockInterview(
                        request.getJobId(),
                        authorization
                );

        log.info("===== MOCK INTERVIEW CHECK =====");
        log.info("response={}", mockInterview);

        if (mockInterview == null) {
            log.error("MockInterview is null");
            throw new ApiServerResponseException("면접 정보를 불러 올 수 없습니다.");
        }

        if (mockInterview.getData() == null) {
            log.error("MockInterview Data is null");
            throw new ApiServerResponseException("면접 정보를 불러 올 수 없습니다.");
        }

        if (mockInterview.getData().getResult() == null) {
            log.error("MockInterview Data Result is null");
            throw new ApiServerResponseException("면접 정보를 불러 올 수 없습니다.");
        }

        if (mockInterview.getData().getResult().getInterview() == null) {
            log.error("MockInterview Data Result Interview is null");
            throw new ApiServerResponseException("면접 정보를 불러올 수 없습니다.");
        }

        log.info(
                "question count={}",
                mockInterview.getData()
                        .getResult()
                        .getInterview()
                        .size()
        );

        List<ChatQuestion> questions =
                mockInterview.getData()
                        .getResult()
                        .getInterview()
                        .stream()
                        .map(q ->
                                ChatQuestion.builder()
                                        .questionId(q.getId().longValue())
                                        .parentId(null)
                                        .type(QuestionType.ORIGINAL)
                                        .intention(q.getCategory())
                                        .content(q.getQuestion())
                                        .createdAt(LocalDateTime.now())
                                        .build()
                        )
                        .toList();

        ChatInterviewSession session = ChatInterviewSession.builder()
                .sessionId(request.getSessionId())
                .interviewId(request.getInterviewId())
                .userId(request.getUserId())
                .personaId(request.getPersonaId())
                .personaType(request.getPersonaType())
                .status(InterviewStatus.IN_PROGRESS)
                .questions(new ArrayList<>(questions))
                .answers(new ArrayList<>())
                .currentQuestionIndex(0)
                .createdAt(LocalDateTime.now())
                .build();

        saveSession(session);

        return ChatInterviewResponse.from(session);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatInterviewAllResponse getInterview(String sessionId) {
        ChatInterviewSession session = getSession(sessionId);

        return ChatInterviewAllResponse.from(session);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatQuestionResponse getCurrentQuestion(String sessionId) {
        ChatInterviewSession session = getSession(sessionId);

        ChatQuestion question = session.getCurrentQuestion();

        if (question == null) {
            throw new InterviewQuestionNotFoundException("질문이 null로 반환 됨");
        }

        return ChatQuestionResponse.from(question);
    }

    @Override
    @Transactional
    public ChatProgressResponse submitAnswer(String sessionId, ChatAnswerRequest request) {

        ChatInterviewSession session = getSession(sessionId);

        ChatQuestion currentQuestion = session.getCurrentQuestion();

        if (currentQuestion == null) {
            return completeInterview(sessionId);
        }

        if (!currentQuestion.getQuestionId().equals(request.getQuestionId())) {
            throw new InterviewQuestionMismatchException("현재 질문과 요청한 질문이 일치하지 않습니다.");
        }

        ChatAnswer answer = ChatAnswer.builder()
                .interviewId(session.getInterviewId())
                .questionId(request.getQuestionId())
                .userId(session.getUserId())
                .responseTime(request.getResponseTime())
                .content(request.getContent())
                .createdAt(LocalDateTime.now())
                .build();

        session.getAnswers().add(answer);

        log.info("[ANSWER SAVED] qId={}", currentQuestion.getQuestionId());

        FollowQuestionAiResponse aiResponse = FollowQuestionAiResponse.notRequired();

        if (canCreateFollowQuestion(session, currentQuestion)) {
            try {
                aiResponse = aiQuestionClient.decideFollowQuestion(
                        FollowQuestionAiRequest.of(
                                session.getSessionId(),
                                session.getInterviewId(),
                                session.getUserId(),
                                session.getPersonaId(),
                                session.getPersonaType(),
                                currentQuestion,
                                request.getContent(),
                                request.getResponseTime()
                        )
                );
            } catch (Exception e) {
                log.error("[AI ERROR]", e);
                aiResponse = FollowQuestionAiResponse.notRequired();
            }
        } else {
            log.info("[FOLLOW SKIPPED] follow question limit reached or current question is follow-up");
        }

        log.info("[AI RESULT] required={}", aiResponse.getRequired());

        if (Boolean.TRUE.equals(aiResponse.getRequired())) {

            ChatQuestion followQuestion = ChatQuestion.builder()
                    .questionId(createFollowQuestionId())
                    .parentId(currentQuestion.getQuestionId())
                    .type(QuestionType.FOLLOW)
                    .intention(aiResponse.getIntention())
                    .content(aiResponse.getContent())
                    .createdAt(LocalDateTime.now())
                    .build();

            session.getQuestions().add(
                    session.getCurrentQuestionIndex() + 1,
                    followQuestion
            );

            log.info("[FOLLOW CREATED] {}", followQuestion.getContent());
        }

        session.moveNextQuestion();

        ChatQuestion nextQuestion = session.getCurrentQuestion();

        if (nextQuestion == null) {

            session.setStatus(InterviewStatus.COMPLETED);

            apiServerClient.saveInterviewResult(ChatInterviewResultSaveRequest.from(session));

            deleteSession(sessionId);

            return ChatProgressResponse.end();
        }

        saveSession(session);

        return ChatProgressResponse.next(nextQuestion);
    }

    @Override
    @Transactional
    public ChatProgressResponse completeInterview(String sessionId) {
        ChatInterviewSession session = getSession(sessionId);

        session.setStatus(InterviewStatus.COMPLETED);

        apiServerClient.saveInterviewResult(
                ChatInterviewResultSaveRequest.from(session)
        );

        deleteSession(sessionId);

        return ChatProgressResponse.end();
    }

    @Override
    @Transactional
    public ChatProgressResponse quitInterview(String sessionId) {
        ChatInterviewSession session = getSession(sessionId);

        session.setStatus(InterviewStatus.ABANDONED);

        apiServerClient.saveInterviewResult(
                ChatInterviewResultSaveRequest.from(session)
        );

        deleteSession(sessionId);

        return ChatProgressResponse.quit();
    }

    private ChatInterviewSession getSession(String sessionId) {
        Object value = redisTemplate.opsForValue().get(createKey(sessionId));

        if (value == null) {
            throw new InterviewSessionNotFoundException("면접 세션을 찾을 수 없습니다.");
        }
        if (!(value instanceof ChatInterviewSession session)) {
            throw new InterviewSessionDataException("면접 세션 데이터가 올바르지 않습니다.");
        }
        return session;
    }

    private void saveSession(ChatInterviewSession session) {
        redisTemplate.opsForValue().set(
                createKey(session.getSessionId()),
                session,
                SESSION_TTL
        );
    }

    private void deleteSession(String sessionId) {
        redisTemplate.delete(createKey(sessionId));
    }

    private boolean canCreateFollowQuestion(ChatInterviewSession session, ChatQuestion currentQuestion) {
        if (currentQuestion.getType() == QuestionType.FOLLOW) {
            return false;
        }

        return countFollowQuestions(session) < maxFollowQuestionCount(session);
    }

    private long maxFollowQuestionCount(ChatInterviewSession session) {
        return countOriginalQuestions(session) / ORIGINAL_QUESTIONS_PER_FOLLOW;
    }

    private long countOriginalQuestions(ChatInterviewSession session) {
        return session.getQuestions().stream()
                .filter(question -> question.getType() == QuestionType.ORIGINAL)
                .count();
    }

    private long countFollowQuestions(ChatInterviewSession session) {
        return session.getQuestions().stream()
                .filter(question -> question.getType() == QuestionType.FOLLOW)
                .count();
    }

    private String createKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }

    private long createFollowQuestionId() {
        return UUID.randomUUID().getMostSignificantBits() | Long.MIN_VALUE;
    }
}
