package com.person.repit.interview.service.company;

import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.person.repit.interview.service.company.CompanyFollowUpTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class CompanyFollowUpDeadlineTest {
    @Test
    void 기업_생성_시간초과는_일반_질문을_유지하고_네트워크를_취소한다() {
        AtomicBoolean cancelled = new AtomicBoolean();
        var harness = harness(enabled(), index -> index == 0 ? Mono.just(response(BASE))
                : Mono.<ClientResponse>never().doOnCancel(() -> cancelled.set(true)));
        StepVerifier.withVirtualTime(() -> harness.client().decideFollowQuestion(request("기업")))
                .expectSubscription().expectNoEvent(Duration.ofMillis(1999)).thenAwait(Duration.ofMillis(1))
                .assertNext(result -> {
                    assertThat(result.getRequired()).isTrue();
                    assertThat(result.getContent()).isEqualTo("일반 질문?");
                }).verifyComplete();
        assertThat(cancelled).isTrue();
        assertThat(harness.bodies()).hasSize(2);
        assertThat(harness.registry().get("repit.anthropic.requests.active").gauge().value()).isZero();
    }

    @Test
    void 기본_판단에_쓴_시간을_뺀_나머지만_기업_생성에_쓴다() {
        var settings = new CompanyFollowUpSettings(true, Duration.ofSeconds(3), Duration.ofSeconds(2), 800);
        var harness = harness(settings, index -> Mono.delay(Duration.ofMillis(index == 0 ? 2500 : 1000))
                .map(ignored -> response(index == 0 ? BASE : COMPANY)));
        StepVerifier.withVirtualTime(() -> harness.client().decideFollowQuestion(request("기업")))
                .expectSubscription().expectNoEvent(Duration.ofMillis(2999)).thenAwait(Duration.ofMillis(1))
                .assertNext(result -> assertThat(result.getContent()).isEqualTo("일반 질문?"))
                .verifyComplete();
        assertThat(harness.bodies()).hasSize(2);
    }

    @Test
    void 기본_판단도_전체_시간을_넘기면_기존_불필요_폴백을_사용한다() {
        var harness = harness(enabled(), index -> Mono.never());
        StepVerifier.withVirtualTime(() -> harness.client().decideFollowQuestion(request("기업")))
                .expectSubscription().expectNoEvent(Duration.ofMillis(4999)).thenAwait(Duration.ofMillis(1))
                .assertNext(result -> assertThat(result.getRequired()).isFalse()).verifyComplete();
        assertThat(harness.bodies()).hasSize(1);
    }

    @Test
    void 기업이_없으면_새_전체_시간제한을_적용하지_않는다() {
        var harness = harness(enabled(), index -> Mono.delay(Duration.ofSeconds(6)).map(ignored -> response(BASE)));
        StepVerifier.withVirtualTime(() -> harness.client().decideFollowQuestion(request(null)))
                .expectSubscription().expectNoEvent(Duration.ofSeconds(5)).thenAwait(Duration.ofSeconds(1))
                .assertNext(result -> assertThat(result.getRequired()).isTrue()).verifyComplete();
        assertThat(harness.bodies()).hasSize(1);
    }

    @Test
    void 남은_시간이_없으면_기업_호출을_시작하지_않는다() throws Exception {
        AtomicBoolean called = new AtomicBoolean();
        var webClient = org.springframework.web.reactive.function.client.WebClient.builder()
                .exchangeFunction(ignored -> { called.set(true); return Mono.never(); }).build();
        var generator = new CompanyFollowUpGenerator(new FollowUpModelClient(MAPPER, webClient),
                MAPPER, "stub", enabled());
        var decision = MAPPER.readValue(BASE, com.person.repit.interview.dto.response.FollowQuestionAiResponse.class);
        StepVerifier.create(generator.generate(request("기업"), decision,
                        FollowUpModelClient.now() - Duration.ofSeconds(6).toNanos()))
                .expectNext(decision).verifyComplete();
        assertThat(called).isFalse();
    }

    @Test
    void 기업_생성까지_슬롯을_유지하고_사용자_취소시_반환한다() {
        AtomicBoolean cancelled = new AtomicBoolean();
        var harness = harness(enabled(), index -> index == 1
                ? Mono.<ClientResponse>never().doOnCancel(() -> cancelled.set(true)) : Mono.just(response(BASE)));
        AtomicBoolean emitted = new AtomicBoolean();
        var pending = harness.client().decideFollowQuestion(request("기업")).subscribe(ignored -> emitted.set(true));
        assertThat(harness.bodies()).hasSize(2);
        assertThat(harness.registry().get("repit.anthropic.requests.active").gauge().value()).isEqualTo(1);
        StepVerifier.create(harness.client().decideFollowQuestion(request(null)))
                .assertNext(result -> assertThat(result.getRequired()).isFalse()).verifyComplete();
        assertThat(harness.bodies()).hasSize(2);
        pending.dispose();
        assertThat(cancelled).isTrue();
        assertThat(emitted).isFalse();
        assertThat(harness.registry().get("repit.anthropic.requests.active").gauge().value()).isZero();
        StepVerifier.create(harness.client().decideFollowQuestion(request(null)))
                .assertNext(result -> assertThat(result.getRequired()).isTrue()).verifyComplete();
        assertThat(harness.bodies()).hasSize(3);
    }
}
