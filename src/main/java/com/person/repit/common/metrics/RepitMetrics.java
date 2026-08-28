package com.person.repit.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component
public class RepitMetrics {

    private final AtomicInteger activeWebSocketConnections = new AtomicInteger();
    private final Counter receivedWebSocketMessages;
    private final Counter processedWebSocketMessages;
    private final Counter failedWebSocketMessages;
    private final Counter successfulAnswers;
    private final Counter failedAnswers;
    private final Counter successfulAnthropicRequests;
    private final Counter failedAnthropicRequests;
    private final Timer answerProcessingTimer;
    private final Timer anthropicRequestTimer;
    private final Timer apiServerRequestTimer;
    private final Timer redisReadTimer;
    private final Timer redisWriteTimer;

    public RepitMetrics(MeterRegistry registry) {
        Gauge.builder("repit.websocket.connections.active", activeWebSocketConnections, AtomicInteger::get)
                .description("현재 활성 WebSocket 연결 수")
                .register(registry);

        receivedWebSocketMessages = counter(registry, "repit.websocket.messages.received", "수신한 WebSocket 메시지 수");
        processedWebSocketMessages = counter(registry, "repit.websocket.messages.processed", "정상 처리한 WebSocket 메시지 수");
        failedWebSocketMessages = counter(registry, "repit.websocket.messages.failed", "처리에 실패한 WebSocket 메시지 수");
        successfulAnswers = counter(registry, "repit.answer.success", "정상 처리한 답변 수");
        failedAnswers = counter(registry, "repit.answer.failure", "처리에 실패한 답변 수");
        successfulAnthropicRequests = counter(registry, "repit.anthropic.requests.success", "성공한 Anthropic 요청 수");
        failedAnthropicRequests = counter(registry, "repit.anthropic.requests.failure", "실패한 Anthropic 요청 수");

        answerProcessingTimer = timer(registry, "repit.answer.processing.duration", "WebSocket 답변 처리 시간");
        anthropicRequestTimer = timer(registry, "repit.anthropic.request.duration", "Anthropic API 호출 시간");
        apiServerRequestTimer = timer(registry, "repit.api.server.request.duration", "API 서버 호출 시간");
        redisReadTimer = timer(registry, "repit.redis.read.duration", "Redis 읽기 시간");
        redisWriteTimer = timer(registry, "repit.redis.write.duration", "Redis 쓰기 시간");
    }

    public void webSocketConnected() {
        activeWebSocketConnections.incrementAndGet();
    }

    public void webSocketDisconnected() {
        activeWebSocketConnections.updateAndGet(value -> Math.max(0, value - 1));
    }

    public void webSocketMessageReceived() {
        receivedWebSocketMessages.increment();
    }

    public void webSocketMessageProcessed() {
        processedWebSocketMessages.increment();
    }

    public void webSocketMessageFailed() {
        failedWebSocketMessages.increment();
    }

    public <T> T recordAnswerProcessing(Supplier<T> operation) {
        try {
            T result = answerProcessingTimer.record(operation);
            successfulAnswers.increment();
            return result;
        } catch (RuntimeException exception) {
            failedAnswers.increment();
            throw exception;
        }
    }

    public <T> T recordAnthropicRequest(Supplier<T> operation) {
        try {
            T result = anthropicRequestTimer.record(operation);
            successfulAnthropicRequests.increment();
            return result;
        } catch (RuntimeException exception) {
            failedAnthropicRequests.increment();
            throw exception;
        }
    }

    public <T> T recordApiServerRequest(Supplier<T> operation) {
        return apiServerRequestTimer.record(operation);
    }

    public void recordApiServerRequest(Runnable operation) {
        apiServerRequestTimer.record(operation);
    }

    public <T> T recordRedisRead(Supplier<T> operation) {
        return redisReadTimer.record(operation);
    }

    public void recordRedisWrite(Runnable operation) {
        redisWriteTimer.record(operation);
    }

    public <T> T recordRedisWrite(Supplier<T> operation) {
        return redisWriteTimer.record(operation);
    }

    private Counter counter(MeterRegistry registry, String name, String description) {
        return Counter.builder(name)
                .description(description)
                .register(registry);
    }

    private Timer timer(MeterRegistry registry, String name, String description) {
        return Timer.builder(name)
                .description(description)
                .publishPercentileHistogram()
                .register(registry);
    }
}
