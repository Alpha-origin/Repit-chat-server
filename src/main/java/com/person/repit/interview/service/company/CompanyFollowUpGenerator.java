package com.person.repit.interview.service.company;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.concurrent.TimeoutException;

@Slf4j
public final class CompanyFollowUpGenerator {
    private final FollowUpModelClient client;
    private final ObjectMapper mapper;
    private final ObjectReader strictReader;
    private final String model;
    private final CompanyFollowUpSettings settings;

    public CompanyFollowUpGenerator(FollowUpModelClient client, ObjectMapper mapper, String model,
                                    CompanyFollowUpSettings settings) {
        this.client = client;
        this.mapper = mapper;
        this.model = model;
        this.settings = settings;
        this.strictReader = mapper.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                .with(JsonParser.Feature.STRICT_DUPLICATE_DETECTION);
    }

    public static boolean hasCompany(FollowQuestionAiRequest request) {
        return StringUtils.hasText(request.getCompanyName()) && StringUtils.hasText(request.getCompanyBrief());
    }

    public Mono<FollowQuestionAiResponse> generate(FollowQuestionAiRequest request, FollowQuestionAiResponse decision,
                                                  long started) {
        return generateWithOutcome(request, decision, started).map(Outcome::response);
    }

    public record Outcome(FollowQuestionAiResponse response, String fallbackReason) {}

    public Mono<Outcome> generateWithOutcome(FollowQuestionAiRequest request, FollowQuestionAiResponse decision,
                                             long started) {
        return Mono.defer(() -> {
            if (!hasCompany(request) || !Boolean.TRUE.equals(decision.getRequired())) {
                return Mono.just(new Outcome(decision, null));
            }
            if (!settings.enabled()) {
                return fallback(request, decision, "disabled");
            }
            if (decision.getScore() == null || decision.getScore() < 0 || decision.getScore() > 5) {
                return fallback(request, decision, "invalid_decision");
            }
            long remaining = settings.totalTimeout().toNanos() - Math.max(0, FollowUpModelClient.now() - started);
            if (remaining <= 0) {
                return fallback(request, decision, "deadline");
            }
            Duration timeout = Duration.ofNanos(Math.min(remaining, settings.generationTimeout().toNanos()));
            return Mono.defer(() -> client.call(CompanyFollowUpPrompt.body(mapper, model, settings.maxTokens(),
                            request, decision), "B"))
                    .map(this::parse)
                    .map(content -> new Outcome(content.merge(decision), null))
                    .timeout(timeout)
                    .doOnNext(ignored -> log.info("follow_up.company status=applied"))
                    .switchIfEmpty(Mono.defer(() -> fallback(request, decision, "empty_response")))
                    .onErrorResume(error -> fallback(request, decision,
                            error instanceof TimeoutException ? "timeout" : "generation_failed"));
        });
    }

    private CompanyFollowUpContent parse(JsonNode response) {
        if (!"end_turn".equals(response.path("stop_reason").asText())) {
            throw new IllegalArgumentException("Incomplete company follow-up response");
        }
        JsonNode blocks = response.path("content");
        if (!blocks.isArray() || blocks.size() != 1 || !"text".equals(blocks.get(0).path("type").asText())
                || !blocks.get(0).path("text").isTextual()) {
            throw new IllegalArgumentException("Unexpected company follow-up response content");
        }
        try {
            return CompanyFollowUpContent.read(strictReader.readTree(blocks.get(0).get("text").textValue()));
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Invalid company follow-up response JSON", error);
        }
    }

    private Mono<Outcome> fallback(FollowQuestionAiRequest request,
                                                   FollowQuestionAiResponse decision, String reason) {
        log.info("follow_up.company status=fallback reason={} sessionId={} interviewId={} questionId={}",
                reason, request.getSessionId(), request.getInterviewId(), request.getQuestionId());
        return Mono.just(new Outcome(decision, reason));
    }
}
