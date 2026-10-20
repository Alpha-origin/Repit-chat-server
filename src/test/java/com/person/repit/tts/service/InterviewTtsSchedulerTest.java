package com.person.repit.tts.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.domain.TtsInput;
import com.person.repit.tts.domain.TtsProfile;
import com.person.repit.tts.domain.VoiceSettings;
import com.person.repit.tts.domain.AudioStatus;
import com.person.repit.tts.dto.InterviewAudioResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.never;

class InterviewTtsSchedulerTest {
    private InterviewTtsScheduler scheduler;
    private TtsClient client;
    private AudioStateRepository repository;
    private AudioStorage storage;

    @BeforeEach
    void setUp() {
        TtsProperties properties = new TtsProperties();
        properties.setEnabled(true);
        TtsInputBuilder inputBuilder = mock(TtsInputBuilder.class);
        when(inputBuilder.build(any(), any())).thenReturn(new TtsInput(
                "질문", "voice", "model", "mp3",
                new VoiceSettings(0.5, 0.8, 0.1, 1.0, true), "1234567890abcdef"
        ));
        client = mock(TtsClient.class);
        when(client.synthesize(any())).thenReturn(Mono.never());
        repository = mock(AudioStateRepository.class);
        when(repository.create(any(AudioState.class))).thenReturn(Mono.just(true));
        when(repository.get(any(), any())).thenAnswer(invocation -> Mono.just(
                AudioState.builder()
                        .sessionId(invocation.getArgument(0))
                        .questionId(invocation.getArgument(1))
                        .status(com.person.repit.tts.domain.AudioStatus.QUEUED)
                        .build()
        ));
        when(repository.save(any(AudioState.class))).thenReturn(Mono.empty());

        storage = mock(AudioStorage.class);
        scheduler = new InterviewTtsScheduler(
                properties,
                inputBuilder,
                client,
                storage,
                repository,
                new RepitMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void 선합성은_세_자리만_사용하고_현재질문_자리를_남긴다() {
        register("session-1", 1L, 2);
        register("session-2", 2L, 2);
        register("session-3", 3L, 2);
        register("session-4", 4L, 2);

        assertThat(scheduler.runningCount()).isEqualTo(3);
        assertThat(scheduler.queuedCount(2)).isEqualTo(1);

        register("session-5", 5L, 0);

        assertThat(scheduler.runningCount()).isEqualTo(4);
        assertThat(scheduler.queuedCount(2)).isEqualTo(1);
    }

    @Test
    void 오디오_한도_초과_코드를_저장하고_조회_응답에_전달하며_재시도하지_않는다() {
        var failed = new AtomicReference<InterviewAudioResponse>();
        when(repository.save(any(AudioState.class))).thenAnswer(invocation -> {
            AudioState state = invocation.getArgument(0);
            if (state.getStatus() == AudioStatus.FAILED) {
                failed.set(InterviewAudioResponse.from(state));
            }
            return Mono.empty();
        });
        when(client.synthesize(any())).thenReturn(Mono.error(new TtsClientException(
                "수신 한도 초과", false, "TTS_AUDIO_TOO_LARGE", null)));

        register("large-audio", 1L, 0);

        assertThat(failed.get()).isNotNull();
        assertThat(failed.get().errorCode()).isEqualTo("TTS_AUDIO_TOO_LARGE");
        verify(client, times(1)).synthesize(any());
        verify(storage, never()).put(any(), any(), any(), any());
    }

    private void register(String sessionId, Long questionId, int priority) {
        ChatQuestion question = ChatQuestion.builder()
                .questionId(questionId)
                .content("질문")
                .build();
        TtsProfile profile = TtsProfile.builder().voiceId("voice").build();
        scheduler.register(sessionId, question, profile, priority, questionId.intValue()).block();
    }
}
