package com.person.repit.tts.service;

import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.service.InterviewOperationLock;
import com.person.repit.tts.domain.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.RedisScript;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class TtsRecoveryConcurrencyTest {
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 같은_면접의_음성을_잠금_충돌_없이_복구하고_개별_실패_후에도_계속한다(boolean failFirst) {
        var redis = mock(ReactiveRedisTemplate.class);
        var values = mock(ReactiveValueOperations.class);
        var stringRedis = mock(ReactiveStringRedisTemplate.class);
        var stringValues = mock(ReactiveValueOperations.class);
        var held = new ConcurrentHashMap<String, String>();
        when(stringRedis.opsForValue()).thenReturn(stringValues);
        when(stringValues.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenAnswer(call -> Mono.fromSupplier(() ->
                        held.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null));
        when(stringRedis.execute(any(RedisScript.class), anyList(), anyList()))
                .thenAnswer(call -> Flux.defer(() -> {
                    List<String> keys = call.getArgument(1);
                    List<String> tokens = call.getArgument(2);
                    return Flux.just(held.remove(keys.getFirst(), tokens.getFirst()) ? 1L : 0L);
                }));
        var states = mock(AudioStateRepository.class);
        var scheduler = mock(InterviewTtsScheduler.class);
        var profiles = mock(PersonaTtsProfileResolver.class);
        var questions = List.of(1L, 2L, 3L).stream().map(id ->
                ChatQuestion.builder().questionId(id).askedByPersonaId(2L).build()).toList();
        var session = ChatInterviewSession.builder().sessionId("s").questions(questions)
                .ttsProfiles(Map.of(2L, TtsProfile.builder().voiceId("stored-voice").build())).build();
        var pendingRead = Sinks.<ChatInterviewSession>one();
        var reads = new AtomicInteger();
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("chat:interview:s")).thenReturn(Mono.defer(() -> {
            if (reads.getAndIncrement() == 0 && failFirst) {
                return Mono.error(new IllegalStateException("read failed"));
            }
            return pendingRead.asMono();
        }));
        when(states.findRecoverable()).thenReturn(Flux.fromIterable(questions).map(question ->
                AudioState.builder().sessionId("s").questionId(question.getQuestionId())
                        .status(AudioStatus.QUEUED).build()));
        when(states.delete(anyString(), anyLong())).thenReturn(Mono.empty());
        when(scheduler.register(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(AudioState.builder().build()));

        new TtsRecoveryService(redis, states, scheduler, profiles,
                new InterviewOperationLock(stringRedis)).recover();
        pendingRead.tryEmitValue(session);

        verify(scheduler, times(failFirst ? 2 : 3)).register(eq("s"), any(), any(), eq(1), anyInt());
        if (failFirst) {
            verify(states, never()).delete("s", 1L);
        }
    }
}
