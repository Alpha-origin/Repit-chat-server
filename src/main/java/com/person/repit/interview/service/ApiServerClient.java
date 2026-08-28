package com.person.repit.interview.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.dto.response.MockInterviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Slf4j
@Component
public class ApiServerClient {

    private final WebClient webClient;
    private final String baseUrl;
    private final RepitMetrics metrics;

    public ApiServerClient(
            @Value("${repit.api-server.base-url}") String baseUrl,
            WebClient.Builder webClientBuilder,
            RepitMetrics metrics
    ) {
        this.baseUrl = baseUrl;
        this.metrics = metrics;

        log.info("API SERVER URL = {}", baseUrl);

        this.webClient = webClientBuilder
                .baseUrl(baseUrl)
                .build();
    }

    public MockInterviewResponse getMockInterview(
            UUID jobId,
            String authorization
    ) {
        return getMockInterviewReactive(jobId, authorization).block();
    }

    public Mono<MockInterviewResponse> getMockInterviewReactive(
            UUID jobId,
            String authorization
    ) {

        log.info("AI REQUEST jobId={}", jobId);

        log.info(
                "REQUEST URL = {}/api/v1/ai?jobId={}",
                baseUrl,
                jobId
        );

        return metrics.recordApiServerRequest(
                webClient.get()
                        .uri("/api/v1/ai?jobId={jobId}", jobId)
                        .header("Authorization", authorization)
                        .retrieve()
                        .bodyToMono(MockInterviewResponse.class)
        );
    }

    public void saveInterviewResult(
            ChatInterviewAllRequest request
    ) {
        saveInterviewResultReactive(request).block();
    }

    public Mono<Void> saveInterviewResultReactive(ChatInterviewAllRequest request) {
        return metrics.recordApiServerRequest(
                webClient.post()
                        .uri("/api/interviews/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .bodyValue(request)
                        .retrieve()
                        .toBodilessEntity()
                        .then()
        );
    }
}
