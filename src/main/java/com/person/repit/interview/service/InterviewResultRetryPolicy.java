package com.person.repit.interview.service;

import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeoutException;

@Component
public class InterviewResultRetryPolicy {
    private static final long[] BACKOFF_SECONDS = {5, 30, 120, 600, 1800};

    public boolean isRetryable(Throwable error) {
        if (error instanceof WebClientResponseException response) {
            int code = response.getStatusCode().value();
            return code == 408 || code == 429 || code >= 500;
        }
        return error instanceof TimeoutException || error instanceof WebClientRequestException;
    }

    public Instant nextAttempt(int attempts, Throwable error, Instant now) {
        long seconds = BACKOFF_SECONDS[Math.min(Math.max(0, attempts - 1), BACKOFF_SECONDS.length - 1)];
        long millis = Math.min(1_800_000L,
                (long) (seconds * 1000 * ThreadLocalRandom.current().nextDouble(0.8, 1.2)));
        Instant backoff = now.plusMillis(millis);
        Instant retryAfter = retryAfter(error, now);
        return retryAfter.isAfter(backoff) ? retryAfter : backoff;
    }

    private Instant retryAfter(Throwable error, Instant now) {
        if (!(error instanceof WebClientResponseException response)
                || response.getStatusCode().value() != 429) {
            return now;
        }
        String value = response.getHeaders().getFirst("Retry-After");
        if (value == null) {
            return now;
        }
        try {
            return now.plusSeconds(Math.max(0, Long.parseLong(value.trim())));
        } catch (RuntimeException ignored) {
            try {
                return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
            } catch (RuntimeException invalidDate) {
                return now;
            }
        }
    }

    public String errorType(Throwable error) {
        return error instanceof WebClientResponseException response
                ? "HTTP_" + response.getStatusCode().value() : error.getClass().getSimpleName();
    }

    public boolean needsAttention(int attempts, Instant firstFailure, Instant now) {
        return attempts >= 10 || !firstFailure.plus(Duration.ofHours(1)).isAfter(now);
    }
}
