package com.person.repit.tts.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InterviewTtsCancellationTest {
    private final TtsClient client = mock(TtsClient.class);
    private final AudioStorage storage = mock(AudioStorage.class);
    private final AudioStateRepository states = mock(AudioStateRepository.class);
    private InterviewTtsScheduler scheduler;

    @BeforeEach
    void setUp() {
        var properties = new TtsProperties();
        properties.setEnabled(true);
        var inputs = mock(TtsInputBuilder.class);
        when(inputs.build(any(), any())).thenReturn(new TtsInput(
                "질문", "voice", "model", "mp3",
                new VoiceSettings(0.5, 0.8, 0.1, 1.0, true), "1234567890abcdef"));
        when(states.create(any())).thenReturn(Mono.just(true));
        when(states.get(anyString(), anyLong())).thenAnswer(call -> Mono.just(AudioState.builder()
                .sessionId(call.getArgument(0)).questionId(call.getArgument(1))
                .status(AudioStatus.QUEUED).build()));
        when(states.save(any())).thenReturn(Mono.empty());
        when(client.synthesize(any())).thenReturn(Mono.never());
        when(storage.put(anyString(), anyLong(), anyString(), any()))
                .thenReturn(Mono.just(new StoredAudio("audio.mp3", "https://example.invalid/audio.mp3")));
        scheduler = new InterviewTtsScheduler(properties, inputs, client, storage, states,
                new RepitMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void 중단하면_실행중_합성과_오디오대기를_취소하고_슬롯을_반환한다() {
        var cancelled = new AtomicBoolean();
        when(client.synthesize(any())).thenReturn(Mono.<byte[]>never()
                .doOnCancel(() -> cancelled.set(true)));
        register("closed", 1L, 0);
        StepVerifier.create(scheduler.await("closed", 1L))
                .then(() -> scheduler.cancelSession("closed"))
                .expectComplete().verify(Duration.ofSeconds(2));
        assertThat(cancelled).isTrue();
        assertThat(scheduler.runningCount()).isZero();
        assertThat(scheduler.hasJob("closed", 1L)).isFalse();
        verifyNoInteractions(storage);
    }

    @Test
    void 중단후_늦은_합성결과는_업로드하지_않는다() {
        var late = Sinks.<byte[]>one();
        when(client.synthesize(any())).thenReturn(late.asMono());
        register("closed", 1L, 0);
        scheduler.cancelSession("closed");
        late.tryEmitValue(new byte[]{1, 2, 3});
        verifyNoInteractions(storage);
        verify(states, never()).save(argThat(state -> state.getStatus() == AudioStatus.READY));
    }

    @Test
    void 중단하면_진행중인_업로드도_취소한다() {
        var cancelled = new AtomicBoolean();
        when(client.synthesize(any())).thenReturn(Mono.just(new byte[]{1}));
        when(storage.put(anyString(), anyLong(), anyString(), any()))
                .thenReturn(Mono.<StoredAudio>never().doOnCancel(() -> cancelled.set(true)));
        register("closed", 1L, 0);
        scheduler.cancelSession("closed");
        assertThat(cancelled).isTrue();
        assertThat(scheduler.runningCount()).isZero();
    }

    @Test
    void 같은_세션의_대기작업을_제거하고_다른_세션은_계속_진행한다() {
        register("closed", 1L, 0);
        register("closed", 2L, 0);
        register("closed", 3L, 0);
        register("closed", 4L, 0);
        register("other", 5L, 0);
        assertThat(scheduler.queuedCount(0)).isEqualTo(2);
        scheduler.cancelSession("closed");
        scheduler.cancelSession("closed");
        assertThat(scheduler.queuedCount(0)).isZero();
        assertThat(scheduler.runningCount()).isEqualTo(1);
        assertThat(scheduler.hasJob("other", 5L)).isTrue();
        verify(client, times(3)).synthesize(any());
    }

    @Test
    void 중단하면_재시도_백오프도_취소한다() {
        var clock = VirtualTimeScheduler.getOrSet();
        try {
            var requests = new AtomicInteger();
            when(client.synthesize(any())).thenReturn(Mono.defer(() -> {
                requests.incrementAndGet();
                return Mono.error(new TtsClientException("일시 실패", true));
            }));
            register("closed", 1L, 0);
            assertThat(requests).hasValue(1);
            scheduler.cancelSession("closed");
            clock.advanceTimeBy(Duration.ofSeconds(10));
            assertThat(requests).hasValue(1);
            assertThat(scheduler.runningCount()).isZero();
            verifyNoInteractions(storage);
        } finally {
            VirtualTimeScheduler.reset();
        }
    }

    @Test
    void 상태저장_대기중에_중단하면_합성을_시작하지_않는다() {
        var saved = Sinks.<Void>empty();
        when(states.save(any())).thenReturn(saved.asMono());
        register("closed", 1L, 0);
        scheduler.cancelSession("closed");
        saved.tryEmitEmpty();
        verifyNoInteractions(client, storage);
        assertThat(scheduler.runningCount()).isZero();
    }

    @Test
    void 즉시_완료되는_정상작업도_슬롯과_작업을_정리한다() {
        when(client.synthesize(any())).thenReturn(Mono.just(new byte[]{1}));
        register("open", 1L, 0);
        assertThat(scheduler.runningCount()).isZero();
        assertThat(scheduler.hasJob("open", 1L)).isFalse();
        verify(states, atLeastOnce()).save(argThat(state -> state.getStatus() == AudioStatus.READY));
    }

    private void register(String sessionId, long questionId, int priority) {
        scheduler.register(sessionId, ChatQuestion.builder().questionId(questionId).content("질문").build(),
                TtsProfile.builder().voiceId("voice").build(), priority, (int) questionId).block();
    }
}
