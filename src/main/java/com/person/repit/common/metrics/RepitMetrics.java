package com.person.repit.common.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Component
public class RepitMetrics {

    private final MeterRegistry registry;
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
    private final Counter spokenTextDigitsRemain;
    private final Counter legacyPersonaProfiles;

    public RepitMetrics(MeterRegistry registry) {
        this.registry = registry;
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
        spokenTextDigitsRemain = counter(registry, "tts.spoken_text.digits_remain", "숫자가 남은 발음 문장 수");
        legacyPersonaProfiles = counter(registry, "tts.persona.legacy", "레거시 TTS 프로필 사용 수");

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

    public <T> Mono<T> recordAnswerProcessing(Mono<T> operation) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start();
            return operation
                    .doOnSuccess(ignored -> successfulAnswers.increment())
                    .doOnError(ignored -> failedAnswers.increment())
                    .doFinally(ignored -> sample.stop(answerProcessingTimer));
        });
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

    public <T> Mono<T> recordAnthropicRequest(Mono<T> operation) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start();
            return operation
                    .doOnSuccess(ignored -> successfulAnthropicRequests.increment())
                    .doOnError(ignored -> failedAnthropicRequests.increment())
                    .doFinally(ignored -> sample.stop(anthropicRequestTimer));
        });
    }

    public <T> T recordApiServerRequest(Supplier<T> operation) {
        return apiServerRequestTimer.record(operation);
    }

    public void recordApiServerRequest(Runnable operation) {
        apiServerRequestTimer.record(operation);
    }

    public <T> Mono<T> recordApiServerRequest(Mono<T> operation) {
        return recordReactive(operation, apiServerRequestTimer);
    }

    public <T> T recordRedisRead(Supplier<T> operation) {
        return redisReadTimer.record(operation);
    }

    public <T> Mono<T> recordRedisRead(Mono<T> operation) {
        return recordReactive(operation, redisReadTimer);
    }

    public void recordRedisWrite(Runnable operation) {
        redisWriteTimer.record(operation);
    }

    public <T> T recordRedisWrite(Supplier<T> operation) {
        return redisWriteTimer.record(operation);
    }

    public <T> Mono<T> recordRedisWrite(Mono<T> operation) {
        return recordReactive(operation, redisWriteTimer);
    }

    public void spokenTextFallback(String reason) {
        Counter.builder("tts.spoken_text.fallback")
                .description("발음 문장 폴백 수")
                .tag("reason", reason)
                .register(registry)
                .increment();
    }

    public void spokenTextDigitsRemain() {
        spokenTextDigitsRemain.increment();
    }

    public void legacyPersonaProfile() {
        legacyPersonaProfiles.increment();
    }

    public <T> Mono<T> recordTtsSynthesis(Mono<T> operation, String result) {
        Timer timer = Timer.builder("tts.synth.duration")
                .tag("result", result)
                .register(registry);
        return recordReactive(operation, timer);
    }

    public void ttsSynthesisRequest(String result) {
        Counter.builder("tts.synth.requests")
                .tag("result", result)
                .register(registry)
                .increment();
    }

    public <T> Mono<T> recordTtsStorage(Mono<T> operation) {
        return recordReactive(operation, Timer.builder("tts.storage.duration").register(registry));
    }

    public void ttsStorageFailure() {
        Counter.builder("tts.storage.failures").register(registry).increment();
    }

    public <T> Mono<T> recordAudioWait(Mono<T> operation, String result) {
        Timer timer = Timer.builder("tts.audio.wait.duration")
                .tag("result", result)
                .register(registry);
        return recordReactive(operation, timer);
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

    private <T> Mono<T> recordReactive(Mono<T> operation, Timer timer) {
        return Mono.defer(() -> {
            Timer.Sample sample = Timer.start();
            return operation.doFinally(ignored -> sample.stop(timer));
        });
    }
}
