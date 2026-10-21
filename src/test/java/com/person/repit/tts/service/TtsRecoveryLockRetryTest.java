package com.person.repit.tts.service;

import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.service.InterviewOperationLock;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.domain.AudioStatus;
import com.person.repit.tts.domain.TtsProfile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class TtsRecoveryLockRetryTest {
    private static final String LOCK_KEY = "chat:interview:operation-lock:s";
    private final ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
    private final ReactiveValueOperations<String, Object> values = mock(ReactiveValueOperations.class);
    private final ReactiveStringRedisTemplate lockRedis = mock(ReactiveStringRedisTemplate.class);
    private final ReactiveValueOperations<String, String> lockValues = mock(ReactiveValueOperations.class);
    private final AudioStateRepository states = mock(AudioStateRepository.class);
    private final InterviewTtsScheduler scheduler = mock(InterviewTtsScheduler.class);
    private final PersonaTtsProfileResolver profiles = mock(PersonaTtsProfileResolver.class);
    private final Map<String, String> held = new ConcurrentHashMap<>();
    private VirtualTimeScheduler clock;
    private ChatInterviewSession session;
    private TtsRecoveryService recovery;

    @BeforeEach
    void setUp() {
        clock = VirtualTimeScheduler.getOrSet();
        when(lockRedis.opsForValue()).thenReturn(lockValues);
        when(lockValues.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(call -> Mono.fromSupplier(() ->
                        held.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null));
        when(lockRedis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenAnswer(call -> Flux.defer(() -> {
                    List<String> keys = call.getArgument(1);
                    List<String> tokens = call.getArgument(2);
                    return Flux.just(held.remove(keys.getFirst(), tokens.getFirst()) ? 1L : 0L);
                }));
        var questions = List.of(1L, 2L, 3L).stream().map(id ->
                ChatQuestion.builder().questionId(id).askedByPersonaId(2L).build()).toList();
        session = ChatInterviewSession.builder().sessionId("s").questions(questions)
                .ttsProfiles(Map.of(2L, TtsProfile.builder().voiceId("stored-voice").build())).build();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("chat:interview:s")).thenReturn(Mono.fromSupplier(() -> session));
        when(states.findRecoverable()).thenReturn(Flux.fromIterable(questions).map(question ->
                AudioState.builder().sessionId("s").questionId(question.getQuestionId())
                        .status(AudioStatus.QUEUED).build()));
        when(states.delete(anyString(), anyLong())).thenReturn(Mono.empty());
        when(scheduler.register(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(AudioState.builder().build()));
        recovery = new TtsRecoveryService(redis, states, scheduler, profiles,
                new InterviewOperationLock(lockRedis));
        held.put(LOCK_KEY, "other-owner");
    }

    @AfterEach
    void resetClock() {
        VirtualTimeScheduler.reset();
    }

    @Test
    void 다른_요청의_잠금이_풀리면_모든_음성을_복구한다() {
        recovery.recover();
        clock.advanceTimeBy(Duration.ofSeconds(3));
        verifyNoInteractions(scheduler);
        verify(values, never()).get(anyString());
        held.remove(LOCK_KEY);
        clock.advanceTimeBy(Duration.ofSeconds(1));
        verify(scheduler, times(3)).register(eq("s"), any(), any(), eq(1), anyInt());
    }

    @Test
    void 재시작_전_잠금의_임대시간만큼_기다린_뒤에도_복구한다() {
        recovery.recover();
        clock.advanceTimeBy(Duration.ofMinutes(2));
        verifyNoInteractions(scheduler);
        held.remove(LOCK_KEY);
        clock.advanceTimeBy(Duration.ofSeconds(1));
        verify(scheduler, times(3)).register(eq("s"), any(), any(), eq(1), anyInt());
    }

    @Test
    void 잠금_대기_중_종료된_면접은_복구하지_않는다() {
        recovery.recover();
        session.setStatus(InterviewStatus.ABANDONED);
        held.remove(LOCK_KEY);
        clock.advanceTimeBy(Duration.ofSeconds(1));
        verifyNoInteractions(scheduler);
        for (long id = 1; id <= 3; id++) {
            verify(states).delete("s", id);
        }
    }
}
