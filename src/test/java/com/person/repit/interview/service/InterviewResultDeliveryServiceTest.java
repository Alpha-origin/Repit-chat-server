package com.person.repit.interview.service;

import com.person.repit.interview.domain.InterviewResultDelivery;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.exception.ApiServerResponseException;
import com.person.repit.interview.type.InterviewStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class InterviewResultDeliveryServiceTest {
    private final InterviewResultOutbox outbox = mock(InterviewResultOutbox.class);
    private final ApiServerClient client = mock(ApiServerClient.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final InterviewResultDeliveryService service = new InterviewResultDeliveryService(
            outbox, client, new InterviewResultRetryPolicy(), meters);
    private InterviewResultDelivery item;

    @BeforeEach
    void setUp() {
        item = new InterviewResultDelivery();
        item.setSessionId("session-id");
        item.setInterviewId(1L);
        item.setInterviewStatus(InterviewStatus.COMPLETED);
        item.setPayload("{\"finalAnswer\":\"saved\"}");
        item.setNextAttemptAt(Instant.EPOCH);
        when(outbox.save(any())).thenReturn(Mono.empty());
    }

    @Test
    void 성공하면_전송완료를_기록하고_답변본문은_제거한다() {
        when(client.saveInterviewPayload(item.getPayload())).thenReturn(Mono.empty());
        var result = service.deliver(item).block();
        assertThat(result.getState()).isEqualTo(InterviewResultDelivery.State.SENT);
        assertThat(result.getPayload()).isNull();
        verify(outbox).save(item);
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 503})
    void 일시실패는_같은_payload를_보존하고_재시도시각을_저장한다(int code) {
        String payload = item.getPayload();
        when(client.saveInterviewPayload(payload)).thenReturn(Mono.error(httpError(code)));
        var result = service.deliver(item).block();
        assertThat(result.getState()).isEqualTo(InterviewResultDelivery.State.PENDING);
        assertThat(result.getPayload()).isEqualTo(payload);
        assertThat(result.getAttempts()).isEqualTo(1);
        assertThat(result.getNextAttemptAt()).isAfter(Instant.now());
    }

    @Test
    void 타임아웃은_대기등록후_기존_성공응답을_반환한다() {
        ChatInterviewAllRequest request = ChatInterviewAllRequest.builder().build();
        when(outbox.enqueue(request)).thenReturn(Mono.just(item));
        when(client.saveInterviewPayload(item.getPayload())).thenReturn(Mono.error(new TimeoutException()));
        assertThat(service.submit(request).block().getStatus()).isEqualTo(InterviewStatus.COMPLETED);
        verify(outbox).save(item);
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 422})
    void 비일시실패는_보존후_오류를_반환하고_자동반복을_중단한다(int code) {
        when(client.saveInterviewPayload(item.getPayload())).thenReturn(Mono.error(httpError(code)));
        StepVerifier.create(service.deliver(item)).expectError(WebClientResponseException.class).verify();
        assertThat(item.getState()).isEqualTo(InterviewResultDelivery.State.BLOCKED);
        assertThat(item.getPayload()).isNotNull();
        verify(outbox).save(item);
        StepVerifier.create(service.deliver(item)).expectError(ApiServerResponseException.class).verify();
        verify(client, times(1)).saveInterviewPayload(any());
    }

    @Test
    void 대기등록_실패시_API를_호출하거나_성공처리하지_않는다() {
        when(outbox.enqueue(any())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        StepVerifier.create(service.submit(ChatInterviewAllRequest.builder().build()))
                .expectError(IllegalStateException.class).verify();
        verifyNoInteractions(client);
    }

    @Test
    void 전송상태_저장실패는_성공으로_숨기지_않는다() {
        when(client.saveInterviewPayload(item.getPayload())).thenReturn(Mono.error(new TimeoutException()));
        when(outbox.save(item)).thenReturn(Mono.error(new IllegalStateException("redis down")));
        StepVerifier.create(service.deliver(item)).expectError(IllegalStateException.class).verify();
    }

    @Test
    void 중복종료요청은_대기기록의_기존응답만_반환하고_재전송하지_않는다() {
        item.setInterviewStatus(InterviewStatus.ABANDONED);
        when(outbox.find("session-id")).thenReturn(Mono.just(item));
        assertThat(service.resume("session-id").block().getStatus()).isEqualTo(InterviewStatus.ABANDONED);
        verifyNoInteractions(client);
    }

    @Test
    void 응답유실후_재시도는_동일본문을_보내고_200을_성공처리한다() {
        String payload = item.getPayload();
        when(client.saveInterviewPayload(payload))
                .thenReturn(Mono.error(new TimeoutException()), Mono.empty());
        service.deliver(item).block();
        item.setNextAttemptAt(Instant.EPOCH);
        service.deliver(item).block();
        verify(client, times(2)).saveInterviewPayload(payload);
        assertThat(item.getState()).isEqualTo(InterviewResultDelivery.State.SENT);
    }

    @Test
    void 연속실패_열번이면_운영알림_지표를_남기고_삭제하지_않는다() {
        item.setAttempts(9);
        when(client.saveInterviewPayload(item.getPayload())).thenReturn(Mono.error(new TimeoutException()));
        service.deliver(item).block();
        assertThat(meters.get("repit.interview.result.attention").counter().count()).isEqualTo(1);
        assertThat(item.getPayload()).isNotNull();
        assertThat(item.getState()).isEqualTo(InterviewResultDelivery.State.PENDING);
    }

    private WebClientResponseException httpError(int code) {
        return WebClientResponseException.create(code, "failure", new HttpHeaders(), new byte[0], null);
    }
}
