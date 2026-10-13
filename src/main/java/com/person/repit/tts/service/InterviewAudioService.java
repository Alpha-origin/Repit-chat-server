package com.person.repit.tts.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.domain.PersonaSnapshot;
import com.person.repit.interview.exception.InterviewQuestionNotFoundException;
import com.person.repit.interview.exception.InterviewSessionDataException;
import com.person.repit.interview.exception.InterviewSessionNotFoundException;
import com.person.repit.interview.type.InterviewRole;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.domain.AudioStatus;
import com.person.repit.tts.domain.TtsProfile;
import com.person.repit.tts.dto.InterviewAudioResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeoutException;

@Service
@RequiredArgsConstructor
public class InterviewAudioService {
    private static final String SESSION_KEY_PREFIX = "chat:interview:";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final AudioStateRepository stateRepository;
    private final InterviewTtsScheduler scheduler;
    private final PersonaTtsProfileResolver profileResolver;
    private final TtsProperties properties;
    private final RepitMetrics metrics;

    public Mono<InterviewAudioResponse> getAudio(String sessionId, Long questionId, Integer waitSeconds) {
        return getSession(sessionId).flatMap(session -> {
            ChatQuestion question = findQuestion(session, questionId);
            if (!properties.isEnabled()) {
                return Mono.just(failed(questionId, "TTS_DISABLED"));
            }
            return stateRepository.get(sessionId, questionId)
                    .flatMap(state -> handleState(session, question, state, waitSeconds))
                    .switchIfEmpty(Mono.defer(() -> registerAndWait(
                            session, question, waitSeconds
                    )));
        });
    }

    private Mono<InterviewAudioResponse> handleState(
            ChatInterviewSession session,
            ChatQuestion question,
            AudioState state,
            Integer waitSeconds
    ) {
        if (state.isTerminal()) {
            return Mono.just(InterviewAudioResponse.from(state));
        }
        if (!scheduler.hasJob(session.getSessionId(), question.getQuestionId())) {
            return registerAndWait(session, question, waitSeconds);
        }
        scheduler.promote(session.getSessionId(), question.getQuestionId());
        return waitForResult(session.getSessionId(), question.getQuestionId(), waitSeconds);
    }

    private Mono<InterviewAudioResponse> registerAndWait(
            ChatInterviewSession session,
            ChatQuestion question,
            Integer waitSeconds
    ) {
        TtsProfile profile = profileFor(session, question.getAskedByPersonaId());
        return scheduler.register(session.getSessionId(), question, profile, 0,
                        session.getQuestions().indexOf(question))
                .flatMap(state -> state.isTerminal()
                        ? Mono.just(InterviewAudioResponse.from(state))
                        : waitForResult(session.getSessionId(), question.getQuestionId(), waitSeconds));
    }

    private Mono<InterviewAudioResponse> waitForResult(
            String sessionId,
            Long questionId,
            Integer requestedWait
    ) {
        Duration wait = normalizeWait(requestedWait);
        if (wait.isZero()) {
            return Mono.just(InterviewAudioResponse.pending(questionId));
        }
        Mono<InterviewAudioResponse> response = scheduler.await(sessionId, questionId)
                .switchIfEmpty(stateRepository.get(sessionId, questionId))
                .map(InterviewAudioResponse::from)
                .timeout(wait)
                .onErrorResume(TimeoutException.class,
                        ignored -> Mono.just(InterviewAudioResponse.pending(questionId)));
        return metrics.recordAudioWait(response, "completed");
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
        return profileResolver.resolve(session.getMode(), legacy);
    }

    private ChatQuestion findQuestion(ChatInterviewSession session, Long questionId) {
        return session.getQuestions().stream()
                .filter(question -> questionId.equals(question.getQuestionId()))
                .findFirst()
                .orElseThrow(() -> new InterviewQuestionNotFoundException(
                        "면접 질문을 찾을 수 없습니다."
                ));
    }

    private Mono<ChatInterviewSession> getSession(String sessionId) {
        return redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + sessionId)
                .switchIfEmpty(Mono.error(new InterviewSessionNotFoundException(
                        "면접 세션을 찾을 수 없습니다."
                )))
                .flatMap(value -> value instanceof ChatInterviewSession session
                        ? Mono.just(session)
                        : Mono.error(new InterviewSessionDataException(
                                "면접 세션 데이터가 올바르지 않습니다."
                        )));
    }

    private Duration normalizeWait(Integer seconds) {
        Duration requested = seconds == null
                ? properties.getLongPoll().getDefaultWait()
                : Duration.ofSeconds(Math.max(0, seconds));
        return requested.compareTo(properties.getLongPoll().getMaxWait()) > 0
                ? properties.getLongPoll().getMaxWait() : requested;
    }

    private InterviewAudioResponse failed(Long questionId, String errorCode) {
        return InterviewAudioResponse.from(AudioState.builder()
                .questionId(questionId).status(AudioStatus.FAILED).errorCode(errorCode)
                .createdAt(Instant.now()).updatedAt(Instant.now()).build());
    }
}
