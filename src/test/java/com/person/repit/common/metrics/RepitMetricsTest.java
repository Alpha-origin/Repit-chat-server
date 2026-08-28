package com.person.repit.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepitMetricsTest {

    private SimpleMeterRegistry registry;
    private RepitMetrics metrics;

    @BeforeEach
    void setUp() {
        registry = new SimpleMeterRegistry();
        metrics = new RepitMetrics(registry);
    }

    @Test
    void recordsWebSocketConnectionsAndMessages() {
        metrics.webSocketConnected();
        metrics.webSocketMessageReceived();
        metrics.webSocketMessageProcessed();
        metrics.webSocketMessageFailed();

        assertThat(registry.get("repit.websocket.connections.active").gauge().value()).isEqualTo(1);
        assertThat(registry.get("repit.websocket.messages.received").counter().count()).isEqualTo(1);
        assertThat(registry.get("repit.websocket.messages.processed").counter().count()).isEqualTo(1);
        assertThat(registry.get("repit.websocket.messages.failed").counter().count()).isEqualTo(1);

        metrics.webSocketDisconnected();

        assertThat(registry.get("repit.websocket.connections.active").gauge().value()).isZero();
    }

    @Test
    void recordsSuccessfulAndFailedAnswers() {
        String result = metrics.recordAnswerProcessing(() -> "success");

        assertThat(result).isEqualTo("success");
        assertThat(registry.get("repit.answer.success").counter().count()).isEqualTo(1);
        assertThat(registry.get("repit.answer.processing.duration").timer().count()).isEqualTo(1);

        assertThatThrownBy(() -> metrics.recordAnswerProcessing(() -> {
            throw new IllegalStateException("failure");
        })).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("repit.answer.failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("repit.answer.processing.duration").timer().count()).isEqualTo(2);
    }

    @Test
    void recordsExternalIoDurations() {
        metrics.recordAnthropicRequest(() -> "response");
        metrics.recordApiServerRequest(() -> "response");
        metrics.recordRedisRead(() -> "session");
        metrics.recordRedisWrite(() -> "saved");

        assertThat(registry.get("repit.anthropic.request.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("repit.anthropic.requests.success").counter().count()).isEqualTo(1);
        assertThat(registry.get("repit.api.server.request.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("repit.redis.read.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("repit.redis.write.duration").timer().count()).isEqualTo(1);
    }

    @Test
    void recordsReactiveExternalIoDurationsAndOutcomes() {
        String response = metrics.recordAnthropicRequest(Mono.just("response")).block();
        metrics.recordApiServerRequest(Mono.just("saved")).block();

        assertThat(response).isEqualTo("response");
        assertThat(registry.get("repit.anthropic.request.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("repit.anthropic.requests.success").counter().count()).isEqualTo(1);
        assertThat(registry.get("repit.api.server.request.duration").timer().count()).isEqualTo(1);

        assertThatThrownBy(() -> metrics.recordAnthropicRequest(
                Mono.<String>error(new IllegalStateException("failure"))
        ).block()).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("repit.anthropic.requests.failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("repit.anthropic.request.duration").timer().count()).isEqualTo(2);
    }

    @Test
    void recordsReactiveAnswerAndRedisDurations() {
        String answer = metrics.recordAnswerProcessing(Mono.just("answer")).block();
        metrics.recordRedisRead(Mono.just("session")).block();
        metrics.recordRedisWrite(Mono.just(true)).block();

        assertThat(answer).isEqualTo("answer");
        assertThat(registry.get("repit.answer.success").counter().count()).isEqualTo(1);
        assertThat(registry.get("repit.answer.processing.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("repit.redis.read.duration").timer().count()).isEqualTo(1);
        assertThat(registry.get("repit.redis.write.duration").timer().count()).isEqualTo(1);

        assertThatThrownBy(() -> metrics.recordAnswerProcessing(
                Mono.<String>error(new IllegalStateException("failure"))
        ).block()).isInstanceOf(IllegalStateException.class);

        assertThat(registry.get("repit.answer.failure").counter().count()).isEqualTo(1);
        assertThat(registry.get("repit.answer.processing.duration").timer().count()).isEqualTo(2);
    }
}
