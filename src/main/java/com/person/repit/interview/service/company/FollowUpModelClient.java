package com.person.repit.interview.service.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;
import reactor.core.scheduler.Schedulers;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public final class FollowUpModelClient {
    private static final Set<String> STOP_REASONS = Set.of(
            "end_turn", "max_tokens", "tool_use", "stop_sequence", "refusal", "pause_turn");
    private final ObjectMapper mapper;
    private final WebClient webClient;

    public FollowUpModelClient(ObjectMapper mapper, WebClient webClient) {
        this.mapper = mapper;
        this.webClient = webClient;
    }

    public Mono<JsonNode> call(Map<String, Object> body, String phase) {
        return Mono.defer(() -> {
            long started = now();
            AtomicBoolean recorded = new AtomicBoolean();
            return webClient.post().uri("/v1/messages").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(body).retrieve().bodyToMono(String.class)
                    .map(this::readResponse)
                    .doOnSuccess(response -> record(recorded, phase,
                            response == null ? "empty_response" : "received", started, response))
                    .doOnError(error -> record(recorded, phase, "request_failed", started, null))
                    .doFinally(signal -> {
                        if (signal == SignalType.CANCEL) {
                            record(recorded, phase, "cancelled", started, null);
                        }
                    });
        });
    }

    public static long now() {
        // Reactor's scheduler clock also permits deterministic deadline tests.
        return Schedulers.parallel().now(TimeUnit.NANOSECONDS);
    }

    private JsonNode readResponse(String body) {
        try {
            JsonNode response = mapper.readTree(body);
            if (response == null || !response.isObject()) {
                throw new IllegalArgumentException("Invalid model response envelope");
            }
            return response;
        } catch (java.io.IOException error) {
            throw new IllegalArgumentException("Invalid model response envelope", error);
        }
    }

    private static void record(AtomicBoolean recorded, String phase, String status, long started, JsonNode response) {
        if (!recorded.compareAndSet(false, true)) {
            return;
        }
        String stop = response == null ? "unknown" : response.path("stop_reason").asText();
        if (!STOP_REASONS.contains(stop)) {
            stop = "unknown";
        }
        long input = response == null ? 0 : Math.max(0, response.path("usage").path("input_tokens").asLong(0));
        long output = response == null ? 0 : Math.max(0, response.path("usage").path("output_tokens").asLong(0));
        log.info("follow_up.call phase={} status={} stop_reason={} latency_ms={} input_tokens={} output_tokens={} call_count=1",
                phase, status, stop, TimeUnit.NANOSECONDS.toMillis(Math.max(0, now() - started)), input, output);
    }
}
