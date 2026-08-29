package com.person.repit.interview.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;

import java.util.concurrent.RejectedExecutionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequestConcurrencyLimiterTest {

    @Test
    void 동시_요청_한도를_넘으면_즉시_거절한다() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        AiRequestConcurrencyLimiter limiter = new AiRequestConcurrencyLimiter(1, registry);
        Disposable runningRequest = limiter.execute(Mono.never()).subscribe();

        assertThatThrownBy(() -> limiter.execute(Mono.just("rejected")).block())
                .isInstanceOf(RejectedExecutionException.class);

        assertThat(registry.counter("repit.anthropic.requests.rejected").count())
                .isEqualTo(1);
        runningRequest.dispose();
    }

    @Test
    void 요청이_종료되면_다음_요청이_허용된다() {
        AiRequestConcurrencyLimiter limiter = new AiRequestConcurrencyLimiter(
                1,
                new SimpleMeterRegistry()
        );

        assertThat(limiter.execute(Mono.just("first")).block())
                .isEqualTo("first");

        assertThat(limiter.execute(Mono.just("second")).block())
                .isEqualTo("second");
    }
}
