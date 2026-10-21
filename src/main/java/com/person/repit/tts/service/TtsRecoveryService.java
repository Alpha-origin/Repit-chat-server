package com.person.repit.tts.service;

import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.domain.PersonaSnapshot;
import com.person.repit.interview.service.InterviewOperationLock;
import com.person.repit.interview.type.InterviewRole;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.domain.TtsProfile;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class TtsRecoveryService {
    private static final String SESSION_KEY_PREFIX = "chat:interview:";

    private final ReactiveRedisTemplate<String, Object> redisTemplate;
    private final AudioStateRepository stateRepository;
    private final InterviewTtsScheduler scheduler;
    private final PersonaTtsProfileResolver profileResolver;
    private final InterviewOperationLock operationLock;

    @EventListener(ApplicationReadyEvent.class)
    public void recover() {
        stateRepository.findRecoverable()
                .flatMap(this::recoverState)
                .onErrorContinue((error, ignored) -> log.warn("TTS 작업 복구 실패", error))
                .subscribe();
    }

    private Mono<Void> recoverState(AudioState state) {
        return operationLock.execute(state.getSessionId(), () ->
                redisTemplate.opsForValue().get(SESSION_KEY_PREFIX + state.getSessionId())
                .ofType(ChatInterviewSession.class)
                .filter(session -> session.getStatus() == InterviewStatus.IN_PROGRESS)
                .flatMap(session -> recoverQuestion(session, state).thenReturn(true))
                .defaultIfEmpty(false)
                .flatMap(recovered -> recovered
                        ? Mono.empty()
                        : stateRepository.delete(state.getSessionId(), state.getQuestionId())));
    }

    private Mono<Void> recoverQuestion(ChatInterviewSession session, AudioState state) {
        ChatQuestion question = session.getQuestions().stream()
                .filter(item -> state.getQuestionId().equals(item.getQuestionId()))
                .findFirst()
                .orElse(null);
        if (question == null) {
            return stateRepository.delete(state.getSessionId(), state.getQuestionId());
        }
        TtsProfile profile = profileFor(session, question.getAskedByPersonaId());
        int order = session.getQuestions().indexOf(question);
        return stateRepository.delete(state.getSessionId(), state.getQuestionId())
                .then(Mono.defer(() -> scheduler.register(session.getSessionId(), question, profile, 1, order)))
                .then();
    }

    private TtsProfile profileFor(ChatInterviewSession session, Long personaId) {
        if (session.getTtsProfiles() != null && session.getTtsProfiles().get(personaId) != null) {
            return session.getTtsProfiles().get(personaId);
        }
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
