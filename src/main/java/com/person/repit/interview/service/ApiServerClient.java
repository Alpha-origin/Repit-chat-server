package com.person.repit.interview.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Duration;


@Slf4j
@Component
public class ApiServerClient {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final WebClient webClient;
    private final String baseUrl;
    private final String internalToken;
    private final RepitMetrics metrics;

    public ApiServerClient(
            @Value("${repit.api-server.base-url}") String baseUrl,
            @Value("${repit.api-server.internal-token:}") String internalToken,
            WebClient.Builder webClientBuilder,
            RepitMetrics metrics
    ) {
        this.baseUrl = baseUrl;
        this.internalToken = internalToken.trim();
        this.metrics = metrics;

        log.info("API SERVER URL = {}", baseUrl);
        if (this.internalToken.isEmpty()) {
            log.warn("면접 기록을 인증 헤더 없이 보냅니다.");
        } else {
            log.info("API INTERNAL TOKEN = 설정됨");
        }

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public Mono<Void> saveInterviewResult(
            ChatInterviewAllRequest request
    ) {
        return sendResult(request);
    }

    public Mono<Void> saveInterviewPayload(String payload) {
        return sendResult(payload);
    }

    private Mono<Void> sendResult(Object payload) {
        return metrics.recordApiServerRequest(
                webClient.post()
                        .uri("/api/interviews/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers -> {
                            if (!internalToken.isEmpty()) {
                                headers.set(INTERNAL_TOKEN_HEADER, internalToken);
                            }
                        })
                        .bodyValue(payload)
                        .retrieve()
                        .onStatus(status -> !status.is2xxSuccessful(), response -> response.createException())
                        .toBodilessEntity()
                        .timeout(Duration.ofSeconds(10))
                        .doOnError(error -> {
                            if (error instanceof WebClientResponseException response) {
                                log.warn("[면접 결과 저장 실패] HTTP 상태={}", response.getStatusCode().value());
                            } else {
                                log.warn("[면접 결과 저장 실패] type={}", error.getClass().getSimpleName());
                            }
                        })
                        .then()
        );
    }
}
