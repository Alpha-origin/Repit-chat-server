package com.person.repit.interview.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.Semaphore;

@Component
public class AiRequestConcurrencyLimiter {

    private final int maxConcurrentRequests;
    private final Semaphore permits;
    private final Counter rejectedRequests;

    public AiRequestConcurrencyLimiter(
            @Value("${anthropic.max-concurrent-requests:50}") int maxConcurrentRequests,
            MeterRegistry registry
    ) {
        this.maxConcurrentRequests = maxConcurrentRequests;
        this.permits = new Semaphore(maxConcurrentRequests, true);
        this.rejectedRequests = Counter.builder("repit.anthropic.requests.rejected")
                .description("동시성 제한으로 빠르게 거절한 Anthropic 요청 수")
                .register(registry);

        Gauge.builder("repit.anthropic.requests.active", permits,
                        semaphore -> maxConcurrentRequests - semaphore.availablePermits())
                .description("현재 실행 중인 Anthropic 요청 수")
                .register(registry);
    }

    public <T> Mono<T> execute(Mono<T> operation) {
        return Mono.defer(() -> {
            if (!permits.tryAcquire()) {
                rejectedRequests.increment();
                return Mono.error(new RejectedExecutionException(
                        "Anthropic 동시 요청 한도를 초과했습니다."
                ));
            }

            return operation.doFinally(ignored -> permits.release());
        });
    }
}
