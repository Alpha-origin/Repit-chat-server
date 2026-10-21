package com.person.repit.interview.service.impl;

import com.person.repit.common.exception.handler.GlobalExceptionHandler;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.controller.ChatInterviewController;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.service.*;
import com.person.repit.interview.type.*;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.service.InterviewTtsScheduler;
import com.person.repit.tts.service.PersonaTtsProfileResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class InterviewInitialStatusTest {
    private final ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
    private final ReactiveValueOperations<String, Object> values = mock(ReactiveValueOperations.class);
    private final InterviewTtsScheduler tts = mock(InterviewTtsScheduler.class);
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(Mono.just(true));
        when(tts.register(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(AudioState.builder().build()));
        var delivery = mock(InterviewResultDeliveryService.class);
        when(delivery.ensureNotFinalized(anyString())).thenReturn(Mono.empty());
        var lock = mock(InterviewOperationLock.class);
        when(lock.execute(anyString(), any())).thenAnswer(call -> ((Supplier<Mono<?>>) call.getArgument(1)).get());
        var service = new ChatInterviewServiceImpl(mock(AiQuestionClient.class), redis, delivery, lock,
                new RepitMetrics(new SimpleMeterRegistry()), mock(PersonaTtsProfileResolver.class), tts);
        client = WebTestClient.bindToController(new ChatInterviewController(service))
                .controllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @CsvSource({"SOLO,COMPLETED", "SOLO,ABANDONED", "MULTI,COMPLETED", "MULTI,ABANDONED"})
    void 종료상태로_새_면접을_생성하면_400을_반환한다(InterviewMode mode, InterviewStatus status) {
        client.post().uri("/chat/interviews").bodyValue(request(mode, status)).exchange()
                .expectStatus().isBadRequest().expectBody().jsonPath("$.code").isEqualTo("INVALID_REQUEST");
        verify(values, never()).setIfAbsent(anyString(), any(), any(Duration.class));
        verifyNoInteractions(tts);
    }

    @ParameterizedTest
    @EnumSource(InterviewMode.class)
    void 정상_진행상태의_준비는_그대로_성공한다(InterviewMode mode) {
        client.post().uri("/chat/interviews").bodyValue(request(mode, InterviewStatus.IN_PROGRESS)).exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("IN_PROGRESS");
    }

    private ChatInterviewPrepareRequest request(InterviewMode mode, InterviewStatus status) {
        var request = new ChatInterviewPrepareRequest();
        request.setSessionId("s");
        request.setInterviewId(1L);
        request.setUserId(2L);
        request.setStatus(status);
        request.setMode(mode);
        request.setPersonality(InterviewType.FRIENDLY);
        request.setTone(InterviewTone.GENTLE);
        request.setMajor(InterviewMajor.BACKEND);
        request.setLevel(InterviewLevel.NORMAL);
        var question = new ChatInterviewPrepareRequest.Question();
        question.setId(3L);
        question.setPersonaId(4L);
        question.setQuestion("질문");
        request.setQuestions(List.of(question));
        return request;
    }
}
