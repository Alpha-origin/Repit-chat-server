package com.person.repit.interview.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class InterviewResultRetryPolicyTest {
    private final InterviewResultRetryPolicy policy = new InterviewResultRetryPolicy();
    private final Instant now = Instant.parse("2026-10-15T01:00:00Z");

    @ParameterizedTest
    @ValueSource(strings = {"3600", "Thu, 15 Oct 2026 02:00:00 GMT"})
    void Retry_After보다_일찍_재시도하지_않는다(String header) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", header);
        var error = WebClientResponseException.create(429, "limited", headers, new byte[0], null);
        assertThat(policy.nextAttempt(1, error, now)).isEqualTo(now.plusSeconds(3600));
    }

    @Test
    void 백오프와_지터를_적용하고_30분을_상한으로_둔다() {
        long[] seconds = {5, 30, 120, 600, 1800, 1800};
        for (int i = 0; i < seconds.length; i++) {
            var next = policy.nextAttempt(i + 1, new java.util.concurrent.TimeoutException(), now);
            assertThat(next).isBetween(now.plusMillis(seconds[i] * 800),
                    now.plusMillis(Math.min(seconds[i] * 1200, 1_800_000)));
        }
    }

    @Test
    void 한시간_경과시_운영확인이_필요하다() {
        assertThat(policy.needsAttention(2, now.minusSeconds(3600), now)).isTrue();
        assertThat(policy.needsAttention(2, now.minusSeconds(3599), now)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"garbage", "-1", "99999999999999999999999"})
    void 잘못된_Retry_After는_기본백오프를_사용한다(String header) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Retry-After", header);
        var error = WebClientResponseException.create(429, "limited", headers, new byte[0], null);
        assertThat(policy.nextAttempt(1, error, now)).isBetween(now.plusSeconds(4), now.plusSeconds(6));
    }
}
