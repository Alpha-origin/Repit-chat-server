package com.person.repit.interview.service.impl;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatAnswer;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.ChatInterviewAllResponse;
import com.person.repit.interview.dto.response.ChatInterviewResponse;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.dto.response.ChatQuestionResponse;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
import com.person.repit.interview.exception.InterviewQuestionMismatchException;
import com.person.repit.interview.exception.InterviewQuestionNotFoundException;
import com.person.repit.interview.exception.InterviewSessionDataException;
import com.person.repit.interview.exception.InterviewSessionNotFoundException;
import com.person.repit.interview.service.AiQuestionClient;
import com.person.repit.interview.service.ApiServerClient;
import com.person.repit.interview.service.ChatInterviewService;
import com.person.repit.interview.type.InterviewLevel;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.QuestionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatInterviewServiceImpl implements ChatInterviewService {

    private static final String KEY_PREFIX = "chat:interview:";
    private static final Duration SESSION_TTL = Duration.ofHours(3);
    private static final int ORIGINAL_QUESTIONS_PER_FOLLOW = 2;

    private final AiQuestionClient aiQuestionClient;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final ApiServerClient apiServerClient;
    private final RepitMetrics metrics;

    @Override
    public Mono<ChatInterviewResponse> prepareInterview(ChatInterviewPrepareRequest request) {
        return Mono.defer(() -> {
            log.debug("sessionId={}, interviewId={}", request.getSessionId(), request.getInterviewId());
            String key = createKey(request.getSessionId());

            var questions = request.getQuestions().stream()
                    .map(question -> ChatQuestion.builder()
                            .questionId(question.getId().longValue())
                            .parentId(null)
                            .type(QuestionType.ORIGINAL)
                            .intention(question.getCategory())
                            .content(question.getQuestion())
                            .expectedAnswer(question.getExpectedAnswer())
                            .basedOn(question.getBasedOn())
                            .askedByPersonaId(question.getPersonaId())
                            .createdAt(LocalDateTime.now())
                            .build())
                    .toList();

            ChatInterviewSession session = ChatInterviewSession.builder()
                    .sessionId(request.getSessionId())
                    .interviewId(request.getInterviewId())
                    .userId(request.getUserId())
                    .level(InterviewLevel.MEDIUM)
                    .status(request.getStatus())
                    .questions(new ArrayList<>(questions))
                    .answers(new ArrayList<>())
                    .currentQuestionIndex(0)
                    .createdAt(LocalDateTime.now())
                    .build();

            return metrics.recordRedisWrite(
                            redisTemplate.opsForValue().setIfAbsent(key, session, SESSION_TTL)
                    )
                    .flatMap(created -> Boolean.TRUE.equals(created)
                            ? Mono.just(ChatInterviewResponse.from(session))
                            : getSession(request.getSessionId()).map(ChatInterviewResponse::from));
        });
    }

    @Override
    public Mono<ChatInterviewAllResponse> getInterview(String sessionId) {
        return getSession(sessionId).map(ChatInterviewAllResponse::from);
    }

    @Override
    public Mono<ChatQuestionResponse> getCurrentQuestion(String sessionId) {
        return getSession(sessionId).flatMap(session -> {
            ChatQuestion question = session.getCurrentQuestion();
            return question == null
                    ? Mono.error(new InterviewQuestionNotFoundException("질문이 null로 반환 됨"))
                    : Mono.just(ChatQuestionResponse.from(question));
        });
    }

    @Override
    public Mono<ChatProgressResponse> submitAnswer(String sessionId, ChatAnswerRequest request) {
        return getSession(sessionId).flatMap(session -> {
            ChatQuestion currentQuestion = session.getCurrentQuestion();
            if (currentQuestion == null) {
                return completeInterview(sessionId);
            }
            if (!currentQuestion.getQuestionId().equals(request.getQuestionId())) {
                return Mono.error(new InterviewQuestionMismatchException(
                        "현재 질문과 요청한 질문이 일치하지 않습니다."
                ));
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
            log.debug("[ANSWER SAVED] qId={}", currentQuestion.getQuestionId());

            return createFollowQuestionIfRequired(session, currentQuestion, request)
                    .flatMap(aiResponse -> moveToNextQuestion(session, currentQuestion, aiResponse));
        });
    }

    @Override
    public Mono<ChatProgressResponse> completeInterview(String sessionId) {
        return getSession(sessionId)
                .flatMap(session -> finishInterview(session, InterviewStatus.COMPLETED, ChatProgressResponse.end()));
    }

    @Override
    public Mono<ChatProgressResponse> quitInterview(String sessionId) {
        return getSession(sessionId)
                .flatMap(session -> finishInterview(session, InterviewStatus.ABANDONED, ChatProgressResponse.quit()));
    }

    private Mono<FollowQuestionAiResponse> createFollowQuestionIfRequired(
            ChatInterviewSession session,
            ChatQuestion currentQuestion,
            ChatAnswerRequest request
    ) {
        if (!canCreateFollowQuestion(session, currentQuestion)) {
            log.info("[FOLLOW SKIPPED] follow question limit reached or current question is follow-up");
            return Mono.just(FollowQuestionAiResponse.notRequired());
        }

        FollowQuestionAiRequest aiRequest = FollowQuestionAiRequest.of(
                session.getSessionId(),
                session.getInterviewId(),
                session.getUserId(),
                currentQuestion.getAskedByPersonaId(),
                session.getLevel(),
                currentQuestion,
                request.getContent(),
                request.getResponseTime()
        );

        return aiQuestionClient.decideFollowQuestion(aiRequest)
                .onErrorResume(exception -> {
                    log.error("[AI ERROR]", exception);
                    return Mono.just(FollowQuestionAiResponse.notRequired());
                });
    }

    private Mono<ChatProgressResponse> moveToNextQuestion(
            ChatInterviewSession session,
            ChatQuestion currentQuestion,
            FollowQuestionAiResponse aiResponse
    ) {
        log.debug("[AI RESULT] required={}", aiResponse.getRequired());

        if (Boolean.TRUE.equals(aiResponse.getRequired())) {
            ChatQuestion followQuestion = ChatQuestion.builder()
                    .questionId(createFollowQuestionId(session))
                    .parentId(currentQuestion.getQuestionId())
                    .type(QuestionType.FOLLOW)
                    .intention(currentQuestion.getIntention())
                    .content(aiResponse.getContent())
                    .expectedAnswer(aiResponse.getExpectedAnswer())
                    .askedByPersonaId(currentQuestion.getAskedByPersonaId())
                    .createdAt(LocalDateTime.now())
                    .build();

            session.getQuestions().add(session.getCurrentQuestionIndex() + 1, followQuestion);
            log.info("[FOLLOW CREATED] {}", followQuestion.getContent());
        }

        session.moveNextQuestion();
        ChatQuestion nextQuestion = session.getCurrentQuestion();
        if (nextQuestion == null) {
            return finishInterview(session, InterviewStatus.COMPLETED, ChatProgressResponse.end());
        }

        return saveSession(session).thenReturn(ChatProgressResponse.next(nextQuestion));
    }

    private Mono<ChatProgressResponse> finishInterview(
            ChatInterviewSession session,
            InterviewStatus status,
            ChatProgressResponse response
    ) {
        session.setStatus(status);
        return apiServerClient.saveInterviewResult(ChatInterviewAllRequest.from(session))
                .then(deleteSession(session.getSessionId()))
                .thenReturn(response);
    }

    private Mono<ChatInterviewSession> getSession(String sessionId) {
        return metrics.recordRedisRead(redisTemplate.opsForValue().get(createKey(sessionId)))
                .switchIfEmpty(Mono.error(new InterviewSessionNotFoundException(
                        "면접 세션을 찾을 수 없습니다."
                )))
                .flatMap(value -> value instanceof ChatInterviewSession session
                        ? Mono.just(session)
                        : Mono.error(new InterviewSessionDataException(
                                "면접 세션 데이터가 올바르지 않습니다."
                        )));
    }

    private Mono<Void> saveSession(ChatInterviewSession session) {
        return metrics.recordRedisWrite(
                        redisTemplate.opsForValue().set(
                                createKey(session.getSessionId()),
                                session,
                                SESSION_TTL
                        )
                )
                .then();
    }

    private Mono<Void> deleteSession(String sessionId) {
        return metrics.recordRedisWrite(redisTemplate.delete(createKey(sessionId))).then();
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

    private long createFollowQuestionId(ChatInterviewSession session) {
        return session.getQuestions().stream()
                .filter(question -> question.getType() == QuestionType.FOLLOW)
                .map(ChatQuestion::getQuestionId)
                .min(Long::compareTo)
                .orElse(0L) - 1L;
    }
}
