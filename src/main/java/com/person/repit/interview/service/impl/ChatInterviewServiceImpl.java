package com.person.repit.interview.service.impl;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.common.exception.InvalidRequestException;
import com.person.repit.interview.domain.ChatAnswer;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.domain.CompanyContext;
import com.person.repit.interview.domain.PersonaSnapshot;
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
import com.person.repit.interview.service.InterviewOperationLock;
import com.person.repit.interview.service.InterviewResultDeliveryService;
import com.person.repit.interview.service.ChatInterviewService;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.InterviewRole;
import com.person.repit.interview.type.QuestionType;
import com.person.repit.tts.domain.TtsProfile;
import com.person.repit.tts.service.PersonaTtsProfileResolver;
import com.person.repit.tts.service.InterviewTtsScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatInterviewServiceImpl implements ChatInterviewService {

    private static final String KEY_PREFIX = "chat:interview:";
    private static final Duration SESSION_TTL = Duration.ofHours(3);
    private static final int ORIGINAL_QUESTIONS_PER_FOLLOW = 2;

    private final AiQuestionClient aiQuestionClient;
    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final InterviewResultDeliveryService resultDelivery;
    private final InterviewOperationLock operationLock;
    private final RepitMetrics metrics;
    private final PersonaTtsProfileResolver profileResolver;
    private final InterviewTtsScheduler ttsScheduler;

    @Override
    public Mono<ChatInterviewResponse> prepareInterview(ChatInterviewPrepareRequest request) {
        return Mono.defer(() -> {
            validateQuestionIds(request);
            validatePersonas(request);
            log.debug("sessionId={}, interviewId={}", request.getSessionId(), request.getInterviewId());
            String key = createKey(request.getSessionId());

            var questions = request.getQuestions().stream()
                    .map(question -> ChatQuestion.builder()
                            .questionId(question.getId().longValue())
                            .parentId(null)
                            .type(QuestionType.ORIGINAL)
                            .category(question.getCategory())
                            .intention(StringUtils.hasText(question.getIntention())
                                    ? question.getIntention()
                                    : null)
                            .content(question.getQuestion())
                            .spokenText(StringUtils.hasText(question.getSpokenText())
                                    ? question.getSpokenText()
                                    : null)
                            .expectedAnswer(question.getExpectedAnswer())
                            .basedOn(question.getBasedOn())
                            .askedByPersonaId(question.getPersonaId())
                            .createdAt(LocalDateTime.now())
                            .build())
                    .toList();

            Map<Long, PersonaSnapshot> personas = createPersonaSnapshots(request);
            Map<Long, TtsProfile> profiles = createTtsProfiles(request, personas);
            ChatInterviewSession session = ChatInterviewSession.builder()
                    .sessionId(request.getSessionId())
                    .interviewId(request.getInterviewId())
                    .userId(request.getUserId())
                    .mode(request.getMode())
                    .personality(request.getPersonality())
                    .tone(request.getTone())
                    .major(request.getMajor())
                    .jobTitle(request.getJobTitle())
                    .level(request.getLevel())
                    .company(toCompany(request.getCompany()))
                    .personas(personas)
                    .ttsProfiles(profiles)
                    .ttsProfileVersion(profiles == null || profiles.isEmpty()
                            ? null
                            : profiles.values().iterator().next().getProfileVersion())
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
                            ? registerOriginalQuestions(session)
                                    .thenReturn(ChatInterviewResponse.from(session))
                            : getSession(request.getSessionId()).map(ChatInterviewResponse::from));
        });
    }

    private CompanyContext toCompany(ChatInterviewPrepareRequest.Company company) {
        return company == null ? null : CompanyContext.builder()
                .displayName(company.getDisplayName())
                .interviewerBrief(company.getInterviewerBrief())
                .build();
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
        return operationLock.execute(sessionId, () -> resultDelivery.resume(sessionId)
                .switchIfEmpty(Mono.defer(() -> submitAnswerOnce(sessionId, request))));
    }

    private Mono<ChatProgressResponse> submitAnswerOnce(String sessionId, ChatAnswerRequest request) {
        return getSession(sessionId).flatMap(session -> {
            if (session.getStatus() != InterviewStatus.IN_PROGRESS) {
                return finishInterview(session, session.getStatus());
            }
            ChatQuestion currentQuestion = session.getCurrentQuestion();
            if (currentQuestion == null) {
                return finishInterview(session, InterviewStatus.COMPLETED);
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

            ChatAnswer previous = session.getAnswers().stream()
                    .filter(item -> item.getQuestionId().equals(request.getQuestionId()))
                    .findFirst().orElse(null);
            if (previous == null) {
                session.getAnswers().add(answer);
            } else if (!java.util.Objects.equals(previous.getContent(), request.getContent())
                    || !java.util.Objects.equals(previous.getResponseTime(), request.getResponseTime())) {
                return Mono.error(new InvalidRequestException("이미 접수한 답변과 다릅니다."));
            }
            log.info(
                    "[ANSWER ACCEPTED] sessionId={}, interviewId={}, questionId={}",
                    session.getSessionId(),
                    session.getInterviewId(),
                    currentQuestion.getQuestionId()
            );

            return saveSession(session)
                    .then(Mono.defer(() -> createFollowQuestionIfRequired(session, currentQuestion, request)))
                    .flatMap(aiResponse -> moveToNextQuestion(session, currentQuestion, aiResponse));
        });
    }

    @Override
    public Mono<ChatProgressResponse> completeInterview(String sessionId) {
        return finishOnce(sessionId, InterviewStatus.COMPLETED);
    }

    @Override
    public Mono<ChatProgressResponse> quitInterview(String sessionId) {
        return finishOnce(sessionId, InterviewStatus.ABANDONED);
    }

    private Mono<ChatProgressResponse> finishOnce(String sessionId, InterviewStatus status) {
        return operationLock.execute(sessionId, () -> resultDelivery.resume(sessionId)
                .switchIfEmpty(Mono.defer(() -> getSession(sessionId)
                        .flatMap(session -> finishInterview(session,
                                session.getStatus() == InterviewStatus.IN_PROGRESS ? status : session.getStatus())))));
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

        PersonaSnapshot persona = session.getPersonas() == null
                ? null : session.getPersonas().get(currentQuestion.getAskedByPersonaId());
        FollowQuestionAiRequest aiRequest = FollowQuestionAiRequest.of(
                session.getSessionId(),
                session.getInterviewId(),
                session.getUserId(),
                currentQuestion.getAskedByPersonaId(),
                persona == null ? session.getPersonality() : persona.getType(),
                persona == null ? session.getTone() : persona.getTone(),
                persona == null ? session.getMajor() : persona.getMajor(),
                session.getMajor(),
                session.getJobTitle(),
                persona == null ? session.getLevel() : persona.getLevel(),
                persona == null ? InterviewRole.TECH : persona.getRole(),
                session.getCompany(),
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
        log.info(
                "[FOLLOW DECISION] sessionId={}, questionId={}, required={}, score={}",
                session.getSessionId(),
                currentQuestion.getQuestionId(),
                aiResponse.getRequired(),
                aiResponse.getScore()
        );

        if (Boolean.TRUE.equals(aiResponse.getRequired())) {
            ChatQuestion followQuestion = ChatQuestion.builder()
                    .questionId(createUniqueFollowQuestionId(session))
                    .parentId(currentQuestion.getQuestionId())
                    .followQuestionId(createFollowQuestionId(
                            session,
                            currentQuestion.getQuestionId()
                    ))
                    .type(QuestionType.FOLLOW)
                    .category(currentQuestion.getCategory())
                    .intention(firstText(
                            aiResponse.getIntention(),
                            currentQuestion.getIntention()
                    ))
                    .content(aiResponse.getContent())
                    .spokenText(StringUtils.hasText(aiResponse.getSpokenText())
                            ? aiResponse.getSpokenText()
                            : null)
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
            return finishInterview(session, InterviewStatus.COMPLETED);
        }

        return saveSession(session)
                .then(registerCurrentQuestion(session))
                .thenReturn(ChatProgressResponse.next(nextQuestion));
    }

    private Mono<ChatProgressResponse> finishInterview(
            ChatInterviewSession session,
            InterviewStatus status
    ) {
        session.setStatus(status);
        // Retain the final answer without expiry even when outbox registration fails.
        return redisTemplate.opsForValue().set(createKey(session.getSessionId()), session)
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(new IllegalStateException("최종 면접 기록 보존 실패")))
                .then(Mono.defer(() -> resultDelivery.submit(ChatInterviewAllRequest.from(session))))
                .flatMap(response -> {
                    ttsScheduler.cancelSession(session.getSessionId());
                    return deleteSession(session.getSessionId())
                            .onErrorResume(error -> {
                                log.warn("종료 세션 정리 실패 sessionId={}, type={}",
                                        session.getSessionId(), error.getClass().getSimpleName());
                                return Mono.empty();
                            })
                            .thenReturn(response);
                });
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
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(new IllegalStateException("면접 답변 저장 실패")))
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

    private long createUniqueFollowQuestionId(ChatInterviewSession session) {
        return session.getQuestions().stream()
                .filter(question -> question.getType() == QuestionType.FOLLOW)
                .map(ChatQuestion::getQuestionId)
                .min(Long::compareTo)
                .orElse(0L) - 1L;
    }

    private long createFollowQuestionId(ChatInterviewSession session, Long originalQuestionId) {
        return session.getQuestions().stream()
                .filter(question -> question.getType() == QuestionType.FOLLOW)
                .filter(question -> originalQuestionId.equals(question.getParentId()))
                .count() + 1L;
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return null;
    }

    private void validateQuestionIds(ChatInterviewPrepareRequest request) {
        var ids = new HashSet<Long>();
        for (ChatInterviewPrepareRequest.Question question : request.getQuestions()) {
            if (!ids.add(question.getId())) {
                throw new InvalidRequestException("질문 ID가 중복되었습니다.");
            }
        }
    }

    private void validatePersonas(ChatInterviewPrepareRequest request) {
        if (request.getPersonas() == null) {
            return;
        }
        var ids = new HashSet<Long>();
        for (ChatInterviewPrepareRequest.Persona persona : request.getPersonas()) {
            if (!ids.add(persona.getPersonaId())) {
                throw new InvalidRequestException("면접관 ID가 중복되었습니다.");
            }
            if (persona.getRole() == InterviewRole.TECH && persona.getMajor() == null) {
                throw new InvalidRequestException("기술 면접관의 전공이 필요합니다.");
            }
        }
        boolean missingPersona = request.getQuestions().stream()
                .anyMatch(question -> !ids.contains(question.getPersonaId()));
        if (missingPersona) {
            throw new InvalidRequestException("질문의 면접관 정보를 찾을 수 없습니다.");
        }
    }

    private Map<Long, PersonaSnapshot> createPersonaSnapshots(ChatInterviewPrepareRequest request) {
        if (request.getPersonas() == null) {
            return null;
        }
        Map<Long, PersonaSnapshot> result = new HashMap<>();
        request.getPersonas().forEach(persona -> result.put(persona.getPersonaId(),
                PersonaSnapshot.builder()
                        .personaId(persona.getPersonaId())
                        .name(persona.getPersonaName())
                        .role(persona.getRole())
                        .major(persona.getMajor())
                        .type(persona.getType())
                        .tone(persona.getTone())
                        .level(persona.getLevel())
                        .career(persona.getCareer())
                        .gender(persona.getGender())
                        .voiceIndex(persona.getVoiceIndex())
                        .build()));
        return result;
    }

    private Map<Long, TtsProfile> createTtsProfiles(
            ChatInterviewPrepareRequest request,
            Map<Long, PersonaSnapshot> personas
    ) {
        if (personas == null) {
            return null;
        }
        Map<Long, TtsProfile> result = new HashMap<>();
        personas.forEach((id, persona) -> result.put(
                id, profileResolver.resolve(persona)
        ));
        return result;
    }

    private Mono<Void> registerOriginalQuestions(ChatInterviewSession session) {
        return Flux.range(0, session.getQuestions().size())
                .flatMap(index -> {
                    ChatQuestion question = session.getQuestions().get(index);
                    int priority = index == 0 ? 0 : index == 1 ? 1 : 2;
                    return registerTts(session, question, priority, index);
                })
                .then();
    }

    private Mono<Void> registerCurrentQuestion(ChatInterviewSession session) {
        ChatQuestion current = session.getCurrentQuestion();
        if (current == null) {
            return Mono.empty();
        }
        int order = session.getCurrentQuestionIndex();
        return registerTts(session, current, 0, order)
                .doOnSuccess(ignored -> {
                    ttsScheduler.changePriority(session.getSessionId(), current.getQuestionId(), 0);
                    session.getQuestions().stream()
                            .skip(order + 1L)
                            .filter(question -> question.getType() == QuestionType.ORIGINAL)
                            .findFirst()
                            .ifPresent(question -> ttsScheduler.changePriority(
                                    session.getSessionId(), question.getQuestionId(), 1
                            ));
                });
    }

    private Mono<Void> registerTts(
            ChatInterviewSession session,
            ChatQuestion question,
            int priority,
            int order
    ) {
        TtsProfile profile = profileFor(session, question.getAskedByPersonaId());
        return ttsScheduler.register(session.getSessionId(), question, profile, priority, order)
                .then()
                .onErrorResume(error -> {
                    log.warn("TTS 등록 실패 sessionId={}, questionId={}",
                            session.getSessionId(), question.getQuestionId(), error);
                    return Mono.empty();
                });
    }

    private TtsProfile profileFor(ChatInterviewSession session, Long personaId) {
        if (session.getTtsProfiles() != null && session.getTtsProfiles().get(personaId) != null) {
            return session.getTtsProfiles().get(personaId);
        }
        metrics.legacyPersonaProfile();
        PersonaSnapshot legacy = PersonaSnapshot.builder()
                .personaId(personaId)
                .role(InterviewRole.TECH)
                .major(session.getMajor())
                .type(session.getPersonality())
                .tone(session.getTone())
                .level(session.getLevel())
                .build();
        return profileResolver.resolve(legacy);
    }
}
