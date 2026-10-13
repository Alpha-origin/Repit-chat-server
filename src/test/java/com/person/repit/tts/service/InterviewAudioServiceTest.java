package com.person.repit.tts.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.exception.InterviewQuestionNotFoundException;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.domain.AudioStatus;
import com.person.repit.tts.dto.InterviewAudioResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class InterviewAudioServiceTest {
    private ReactiveValueOperations<String, Object> values;
    private AudioStateRepository states;
    private InterviewTtsScheduler scheduler;
    private TtsProperties properties;
    private InterviewAudioService service;

    @BeforeEach
    void setUp() {
        ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
        values = mock(ReactiveValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        states = mock(AudioStateRepository.class);
        scheduler = mock(InterviewTtsScheduler.class);
        properties = new TtsProperties();
        service = new InterviewAudioService(
                redis,
                states,
                scheduler,
                mock(PersonaTtsProfileResolver.class),
                properties,
                new RepitMetrics(new SimpleMeterRegistry())
        );
        when(values.get("chat:interview:session-id")).thenReturn(Mono.just(session()));
    }

    @Test
    void 비활성화하면_실패_상태를_즉시_반환한다() {
        properties.setEnabled(false);

        InterviewAudioResponse response = service.getAudio("session-id", 1L, 20).block();

        assertThat(response.status()).isEqualTo(AudioStatus.FAILED);
        assertThat(response.errorCode()).isEqualTo("TTS_DISABLED");
        verifyNoInteractions(states, scheduler);
    }

    @Test
    void 준비된_오디오는_URL을_즉시_반환한다() {
        properties.setEnabled(true);
        when(states.get("session-id", 1L)).thenReturn(Mono.just(AudioState.builder()
                .questionId(1L)
                .status(AudioStatus.READY)
                .url("https://audio.example/1.mp3")
                .build()));

        InterviewAudioResponse response = service.getAudio("session-id", 1L, 20).block();

        assertThat(response.status()).isEqualTo(AudioStatus.READY);
        assertThat(response.url()).isEqualTo("https://audio.example/1.mp3");
    }

    @Test
    void 대기_작업을_승격하고_즉시조회면_PENDING을_반환한다() {
        properties.setEnabled(true);
        when(states.get("session-id", 1L)).thenReturn(Mono.just(AudioState.builder()
                .sessionId("session-id")
                .questionId(1L)
                .status(AudioStatus.QUEUED)
                .build()));
        when(scheduler.hasJob("session-id", 1L)).thenReturn(true);

        InterviewAudioResponse response = service.getAudio("session-id", 1L, 0).block();

        assertThat(response.status()).isEqualTo(AudioStatus.PENDING);
        verify(scheduler).changePriority("session-id", 1L, 0);
    }

    @Test
    void 세션에_없는_질문은_404_예외로_처리한다() {
        properties.setEnabled(true);

        StepVerifier.create(service.getAudio("session-id", 999L, 0))
                .expectError(InterviewQuestionNotFoundException.class)
                .verify();
    }

    private ChatInterviewSession session() {
        ChatQuestion question = ChatQuestion.builder()
                .questionId(1L)
                .content("질문")
                .askedByPersonaId(1L)
                .build();
        return ChatInterviewSession.builder()
                .sessionId("session-id")
                .questions(List.of(question))
                .build();
    }
}
