package com.person.repit.interview.service.company;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public record CompanyFollowUpSettings(
        @Value("${anthropic.company-follow-up-enabled:false}") boolean enabled,
        @Value("${anthropic.company-follow-up-total-timeout:5s}") Duration totalTimeout,
        @Value("${anthropic.company-follow-up-generation-timeout:2s}") Duration generationTimeout,
        @Value("${anthropic.company-follow-up-max-tokens:800}") int maxTokens
) {
    public CompanyFollowUpSettings {
        requirePositive(totalTimeout, "total-timeout");
        requirePositive(generationTimeout, "generation-timeout");
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("company-follow-up-max-tokens must be positive");
        }
    }

    public static CompanyFollowUpSettings defaults() {
        return new CompanyFollowUpSettings(false, Duration.ofSeconds(5), Duration.ofSeconds(2), 800);
    }

    private static void requirePositive(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("company-follow-up-" + name + " must be positive");
        }
        try {
            value.toNanos();
        } catch (ArithmeticException error) {
            throw new IllegalArgumentException("company-follow-up-" + name + " is too large", error);
        }
    }
}
