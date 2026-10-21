package com.person.repit.interview.service.impl;

import com.person.repit.common.config.JacksonConfig;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.InterviewResultDelivery;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.exception.InterviewSessionAlreadyExistsException;
import com.person.repit.interview.service.*;
import com.person.repit.interview.type.*;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.service.InterviewTtsScheduler;
import com.person.repit.tts.service.PersonaTtsProfileResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;
import org.springframework.data.redis.core.*;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.stream.Stream;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class FinalizedInterviewPreparationTest {
    private final ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
    private final ReactiveValueOperations<String, Object> values = mock(ReactiveValueOperations.class);
    private final ReactiveStringRedisTemplate strings = mock(ReactiveStringRedisTemplate.class);
    private final ReactiveValueOperations<String, String> records = mock(ReactiveValueOperations.class);
    private final ApiServerClient api = mock(ApiServerClient.class);
    private final InterviewTtsScheduler tts = mock(InterviewTtsScheduler.class);
    private final AiQuestionClient ai = mock(AiQuestionClient.class);
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new JacksonConfig().objectMapper();
    private ChatInterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(strings.opsForValue()).thenReturn(records);
        when(records.get(anyString())).thenReturn(Mono.empty());
        when(values.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(Mono.just(true));
        when(tts.register(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(AudioState.builder().build()));
        var lock = mock(InterviewOperationLock.class);
        when(lock.execute(anyString(), any())).thenAnswer(call ->
                ((Supplier<Mono<?>>) call.getArgument(1)).get());
        var meters = new SimpleMeterRegistry();
        var delivery = new InterviewResultDeliveryService(new InterviewResultOutbox(strings, mapper),
                api, new InterviewResultRetryPolicy(), meters);
        service = new ChatInterviewServiceImpl(ai, redis, delivery, lock, new RepitMetrics(meters),
                mock(PersonaTtsProfileResolver.class), tts);
    }

    static Stream<Arguments> finalizedCases() {
        return Stream.of(InterviewMode.values()).flatMap(mode ->
                Stream.of(InterviewStatus.COMPLETED, InterviewStatus.ABANDONED).flatMap(status ->
                        Stream.of(InterviewResultDelivery.State.values())
                                .map(state -> Arguments.of(mode, status, state))));
    }

    @ParameterizedTest
    @MethodSource("finalizedCases")
    void 종료기록이_있으면_모드와_전송상태에_관계없이_재생성을_거절한다(
            InterviewMode mode, InterviewStatus status, InterviewResultDelivery.State state) throws Exception {
        var item = receipt(status, state);
        String original = mapper.writeValueAsString(item);
        when(records.get("chat:interview:result:session-id")).thenReturn(Mono.just(original));

        StepVerifier.create(service.prepareInterview(request(mode)))
                .expectError(InterviewSessionAlreadyExistsException.class).verify(Duration.ofSeconds(2));

        verify(values, never()).setIfAbsent(anyString(), any(), any(Duration.class));
        verify(records, never()).set(anyString(), anyString());
        verifyNoInteractions(tts, ai, api);
        assertThat(mapper.writeValueAsString(item)).isEqualTo(original);
    }

    @Test
    void 종료후_다른_식별자로_같은_세션을_준비해도_거절한다() throws Exception {
        var item = receipt(InterviewStatus.COMPLETED, InterviewResultDelivery.State.SENT);
        when(records.get(anyString())).thenReturn(Mono.just(mapper.writeValueAsString(item)));
        var request = request(InterviewMode.SOLO);
        request.setInterviewId(999L);
        request.setUserId(888L);
        StepVerifier.create(service.prepareInterview(request))
                .expectError(InterviewSessionAlreadyExistsException.class).verify();
        verify(values, never()).setIfAbsent(anyString(), any(), any(Duration.class));
        verifyNoInteractions(tts, ai, api);
    }

    @Test
    void 전송기록을_읽지_못하면_세션을_새로_만들지_않는다() {
        when(records.get(anyString())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        StepVerifier.create(service.prepareInterview(request(InterviewMode.SOLO)))
                .expectError(IllegalStateException.class).verify();
        verify(values, never()).setIfAbsent(anyString(), any(), any(Duration.class));
        verifyNoInteractions(tts, ai, api);
    }

    @Test
    void 구_기록의_종료상태가_비어도_그_세션을_재사용하지_않는다() throws Exception {
        var item = receipt(null, InterviewResultDelivery.State.SENT);
        when(records.get(anyString())).thenReturn(Mono.just(mapper.writeValueAsString(item)));
        StepVerifier.create(service.prepareInterview(request(InterviewMode.SOLO)))
                .expectError(InterviewSessionAlreadyExistsException.class).verify();
        verify(values, never()).setIfAbsent(anyString(), any(), any(Duration.class));
        verify(records, never()).set(anyString(), anyString());
        verifyNoInteractions(tts, ai, api);
    }

    @Test
    void 종료기록이_없는_새_준비요청은_기존대로_성공한다() {
        var response = service.prepareInterview(request(InterviewMode.SOLO)).block();
        assertThat(response.getStatus()).isEqualTo(InterviewStatus.IN_PROGRESS);
        assertThat(response.getInterviewId()).isEqualTo(1L);
        assertThat(response.getUserId()).isEqualTo(2L);
        verify(tts).register(eq("session-id"), any(), any(), eq(0), eq(0));
    }

    private InterviewResultDelivery receipt(InterviewStatus status, InterviewResultDelivery.State state) {
        var item = new InterviewResultDelivery();
        item.setSessionId("session-id");
        item.setInterviewId(1L);
        item.setInterviewStatus(status);
        item.setState(state);
        item.setNextAttemptAt(Instant.EPOCH);
        item.setPayload(state == InterviewResultDelivery.State.SENT ? null : "original-payload");
        return item;
    }

    private ChatInterviewPrepareRequest request(InterviewMode mode) {
        var request = new ChatInterviewPrepareRequest();
        request.setSessionId("session-id");
        request.setInterviewId(1L);
        request.setUserId(2L);
        request.setStatus(InterviewStatus.IN_PROGRESS);
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
