package com.person.repit.interview.service.impl;

import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.dto.request.ChatInterviewResultSaveRequest;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.ChatInterviewResponse;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.dto.response.ChatQuestionResponse;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
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
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatInterviewServiceImpl implements ChatInterviewService {

    private static final String KEY_PREFIX = "chat:interview:";
    private static final Duration SESSION_TTL = Duration.ofHours(3);

    private final AiQuestionClient aiQuestionClient;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ApiServerClient apiServerClient;
    private final AtomicLong followQuestionId = new AtomicLong(-1);

    @Override
    public ChatInterviewResponse prepareInterview(ChatInterviewPrepareRequest request) {
        String key = createKey(request.getSessionId());

        if (Boolean.TRUE.equals(redisTemplate.hasKey(key))) {
            throw new IllegalArgumentException("이미 준비된 면접 세션입니다.");
        }

        ArrayList<ChatQuestion> questions = new ArrayList<>(
                request.getQuestions().stream()
                        .map(question -> ChatQuestion.builder()
                                .questionId(question.getQuestionId())
                                .parentId(null)
                                .type(QuestionType.ORIGINAL)
                                .intention(question.getIntention())
                                .content(question.getContent())
                                .createdAt(LocalDateTime.now())
                                .build())
                        .toList()
        );

        ChatInterviewSession session = ChatInterviewSession.builder()
                .sessionId(request.getSessionId())
                .interviewId(request.getInterviewId())
                .userId(request.getUserId())
                .personaId(request.getPersonaId())
                .personaType(request.getPersonaType())
                .status(InterviewStatus.IN_PROGRESS)
                .questions(questions)
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
            throw new IllegalArgumentException("현재 진행할 질문이 없습니다.");
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

        log.info("[AI TRIGGER] session={}, qId={}", sessionId, currentQuestion.getQuestionId());

        FollowQuestionAiResponse aiResponse = null;

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

            log.info("[AI RESPONSE] {}", aiResponse);

        } catch (Exception e) {
            log.error("[AI ERROR]", e);
        }
        if (aiResponse != null && Boolean.TRUE.equals(aiResponse.getRequired())) {

            ChatQuestion followQuestion = createFollowQuestion(currentQuestion, aiResponse);

            session.getQuestions().add(
                    session.getCurrentQuestionIndex() + 1,
                    followQuestion
            );

            log.info("[FOLLOW QUESTION CREATED] {}", followQuestion.getContent());
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

        apiServerClient.saveInterviewResult(ChatInterviewResultSaveRequest.from(session), "anything");
        deleteSession(sessionId);

        return ChatProgressResponse.end();
    }

    @Override
    public ChatProgressResponse quitInterview(String sessionId) {
        ChatInterviewSession session = getSession(sessionId);
        session.setStatus(InterviewStatus.ABANDONED);

        apiServerClient.saveInterviewResult(ChatInterviewResultSaveRequest.from(session), "anything");
        deleteSession(sessionId);

        return ChatProgressResponse.quit();
    }

    private boolean canCreateFollowQuestion(ChatInterviewSession session, ChatQuestion currentQuestion) {
        return true;
    }

    private int calculateQuestionDepth(ChatInterviewSession session, ChatQuestion question) {
        int depth = 1;
        Long parentId = question.getParentId();

        while (parentId != null) {
            ChatQuestion parentQuestion = findQuestionById(session, parentId);

            if (parentQuestion == null) {
                break;
            }

            depth++;
            parentId = parentQuestion.getParentId();
        }

        return depth;
    }

    private ChatQuestion findQuestionById(ChatInterviewSession session, Long questionId) {
        return session.getQuestions()
                .stream()
                .filter(question -> questionId.equals(question.getQuestionId()))
                .findFirst()
                .orElse(null);
    }

    private ChatQuestion createFollowQuestion(
            ChatQuestion parentQuestion,
            FollowQuestionAiResponse aiResponse
    ) {
        return ChatQuestion.builder()
                .questionId(followQuestionId.getAndDecrement())
                .parentId(parentQuestion.getQuestionId())
                .type(QuestionType.FOLLOW)
                .intention(aiResponse.getIntention())
                .content(aiResponse.getContent())
                .createdAt(LocalDateTime.now())
                .build();
    }

    private ChatInterviewSession getSession(String sessionId) {
        Object value = redisTemplate.opsForValue().get(createKey(sessionId));

        if (!(value instanceof ChatInterviewSession session)) {
            throw new IllegalArgumentException("존재하지 않는 면접 세션입니다.");
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