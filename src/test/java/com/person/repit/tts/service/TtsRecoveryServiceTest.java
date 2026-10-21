package com.person.repit.tts.service;

import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.service.InterviewOperationLock;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.tts.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class TtsRecoveryServiceTest {
    private final ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
    private final ReactiveValueOperations<String, Object> values = mock(ReactiveValueOperations.class);
    private final AudioStateRepository states = mock(AudioStateRepository.class);
    private final InterviewTtsScheduler scheduler = mock(InterviewTtsScheduler.class);
    private final PersonaTtsProfileResolver profiles = mock(PersonaTtsProfileResolver.class);
    private final InterviewOperationLock lock = mock(InterviewOperationLock.class);
    private final AtomicReference<ChatInterviewSession> session = new AtomicReference<>();
    private final AudioState state = AudioState.builder().sessionId("s").questionId(1L)
            .status(AudioStatus.GENERATING).build();
    private TtsRecoveryService recovery;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("chat:interview:s")).thenReturn(Mono.fromSupplier(session::get));
        when(states.findRecoverable()).thenReturn(Flux.just(state));
        when(states.delete(anyString(), anyLong())).thenReturn(Mono.empty());
        when(scheduler.register(anyString(), any(), any(), anyInt(), anyInt())).thenReturn(Mono.just(state));
        when(lock.execute(anyString(), any())).thenAnswer(call ->
                Mono.defer(() -> ((Supplier<Mono<?>>) call.getArgument(1)).get()));
        session.set(ChatInterviewSession.builder().sessionId("s")
                .questions(List.of(ChatQuestion.builder().questionId(1L).askedByPersonaId(2L).build()))
                .ttsProfiles(Map.of(2L, TtsProfile.builder().voiceId("stored-voice").build())).build());
        recovery = new TtsRecoveryService(redis, states, scheduler, profiles, lock);
    }

    @ParameterizedTest
    @NullSource
    @EnumSource(value = InterviewStatus.class, names = {"COMPLETED", "ABANDONED"})
    void 종료되었거나_상태가_없는_세션은_오디오를_복구하지_않는다(InterviewStatus status) {
        session.get().setStatus(status);
        recovery.recover();
        verify(states).delete("s", 1L);
        verifyNoInteractions(scheduler, profiles);
    }

    @ParameterizedTest
    @EnumSource(value = AudioStatus.class, names = {"QUEUED", "GENERATING"})
    void 진행중인_면접의_음성은_저장된_프로필로_복구한다(AudioStatus status) {
        state.setStatus(status);
        recovery.recover();
        verify(states).delete("s", 1L);
        verify(scheduler).register(eq("s"), any(), eq(session.get().getTtsProfiles().get(2L)), eq(1), eq(0));
        verifyNoInteractions(profiles);
    }

    @Test
    void 세션이나_질문이_없으면_남은_음성상태만_정리한다() {
        session.set(null);
        recovery.recover();
        verify(states).delete("s", 1L);
        verifyNoInteractions(scheduler);
        clearInvocations(states);
        session.set(ChatInterviewSession.builder().sessionId("s").build());
        recovery.recover();
        verify(states).delete("s", 1L);
        verifyNoInteractions(scheduler);
    }

    @Test
    void 잠금_획득전에_종료된_면접도_복구하지_않는다() {
        var acquired = Sinks.<Void>empty();
        when(lock.execute(anyString(), any())).thenAnswer(call -> acquired.asMono()
                .then(Mono.defer(() -> ((Supplier<Mono<?>>) call.getArgument(1)).get())));
        recovery.recover();
        verify(values, never()).get(anyString());
        session.get().setStatus(InterviewStatus.ABANDONED);
        acquired.tryEmitEmpty();
        verify(states).delete("s", 1L);
        verifyNoInteractions(scheduler, profiles);
    }

    @Test
    void 이전_복구_실패가_다른_세션의_복구를_막지_않는다() {
        var failed = AudioState.builder().sessionId("failed").questionId(9L).status(AudioStatus.QUEUED).build();
        when(states.findRecoverable()).thenReturn(Flux.just(failed, state));
        when(values.get("chat:interview:failed")).thenReturn(Mono.error(new IllegalStateException("read failed")));
        recovery.recover();
        verify(scheduler).register(eq("s"), any(), any(), eq(1), eq(0));
        verify(states, never()).delete("failed", 9L);
    }
}
