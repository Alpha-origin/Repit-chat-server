package com.person.repit.interview.service.impl;

import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.dto.request.ChatInterviewResultSaveRequest;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.*;
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

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatInterviewServiceImpl implements ChatInterviewService {

    private static final String KEY_PREFIX = "chat:interview:";
    private static final Duration SESSION_TTL = Duration.ofHours(3);
    private static final AtomicLong followQuestionId = new AtomicLong(-1);

    private final AiQuestionClient aiQuestionClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApiServerClient apiServerClient;


    @Override
    public ChatInterviewResponse prepareInterview(ChatInterviewPrepareRequest request) {
        log.info("jobId={}", request.getJobId());

        log.info(
                "sessionId={}, interviewId={}, jobId={}",
                request.getSessionId(),
                request.getInterviewId(),
                request.getJobId()
        );

        String key = createKey(request.getSessionId());

        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new IllegalArgumentException("이미 존재하는 세션입니다.");
        }

        MockInterviewResponse mockInterview =
                apiServerClient.getMockInterview(
                        request.getJobId()
                );

        log.info("mockInterview={}", mockInterview);
        log.info("data={}", mockInterview.getData());

        if (mockInterview.getData() != null) {
            log.info("result={}", mockInterview.getData().getResult());
        }

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
    public ChatQuestionResponse getCurrentQuestion(String sessionId) {
        ChatInterviewSession session = getSession(sessionId);

        ChatQuestion question = session.getCurrentQuestion();

        if (question == null) {
            throw new IllegalArgumentException("질문 없음");
        }

        return ChatQuestionResponse.from(question);
    }

    @Override
    public ChatProgressResponse submitAnswer(String sessionId, ChatAnswerRequest request) {

        ChatInterviewSession session = getSession(sessionId);

        ChatQuestion currentQuestion = session.getCurrentQuestion();

        if (currentQuestion == null) {
            return completeInterview(sessionId);
        }

        if (!currentQuestion.getQuestionId().equals(request.getQuestionId())) {
            throw new IllegalArgumentException("질문 불일치");
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

        FollowQuestionAiResponse aiResponse;

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

        log.info("[AI RESULT] required={}", aiResponse.getRequired());

        if (Boolean.TRUE.equals(aiResponse.getRequired())) {

            ChatQuestion followQuestion = ChatQuestion.builder()
                    .questionId(followQuestionId.getAndDecrement())
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

            apiServerClient.saveInterviewResult(
                    ChatInterviewResultSaveRequest.from(session),
                    "anything"
            );

            deleteSession(sessionId);

            return ChatProgressResponse.end();
        }

        saveSession(session);

        return ChatProgressResponse.next(nextQuestion);
    }

    @Override
    public ChatProgressResponse completeInterview(String sessionId) {
        ChatInterviewSession session = getSession(sessionId);

        session.setStatus(InterviewStatus.COMPLETED);

        apiServerClient.saveInterviewResult(
                ChatInterviewResultSaveRequest.from(session),
                "anything"
        );

        deleteSession(sessionId);

        return ChatProgressResponse.end();
    }

    @Override
    public ChatProgressResponse quitInterview(String sessionId) {
        ChatInterviewSession session = getSession(sessionId);

        session.setStatus(InterviewStatus.ABANDONED);

        apiServerClient.saveInterviewResult(
                ChatInterviewResultSaveRequest.from(session),
                "anything"
        );

        deleteSession(sessionId);

        return ChatProgressResponse.quit();
    }

    private ChatInterviewSession getSession(String sessionId) {
        Object value = redisTemplate.opsForValue().get(createKey(sessionId));

        if (!(value instanceof ChatInterviewSession session)) {
            throw new IllegalArgumentException("세션 없음");
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

    private String createKey(String sessionId) {
        return KEY_PREFIX + sessionId;
    }
}