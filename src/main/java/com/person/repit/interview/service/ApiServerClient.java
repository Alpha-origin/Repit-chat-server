package com.person.repit.interview.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.dto.response.MockInterviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.UUID;

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

    public Mono<MockInterviewResponse> getMockInterview(
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

    public Mono<Void> saveInterviewResult(
            ChatInterviewAllRequest request
    ) {
        int qnaCount = request.getQnaRequests() == null
                ? 0
                : request.getQnaRequests().size();

        log.info(
                "[면접 결과 저장 요청] URL={}/api/interviews/result, sessionId={}, interviewId={}, 상태={}, 문답 수={}",
                baseUrl,
                request.getSessionId(),
                request.getInterviewId(),
                request.getStatus(),
                qnaCount
        );

        return metrics.recordApiServerRequest(
                webClient.post()
                        .uri("/api/interviews/result")
                        .contentType(MediaType.APPLICATION_JSON)
                        .headers(headers -> {
                            if (!internalToken.isEmpty()) {
                                headers.set(INTERNAL_TOKEN_HEADER, internalToken);
                            }
                        })
                        .bodyValue(request)
                        .retrieve()
                        .toBodilessEntity()
                        .doOnNext(response -> log.info(
                                "[면접 결과 저장 성공] sessionId={}, interviewId={}, HTTP 상태={}",
                                request.getSessionId(),
                                request.getInterviewId(),
                                response.getStatusCode().value()
                        ))
                        .doOnError(
                                WebClientResponseException.class,
                                exception -> log.error(
                                        "[면접 결과 저장 실패] sessionId={}, interviewId={}, HTTP 상태={}, 응답={}",
                                        request.getSessionId(),
                                        request.getInterviewId(),
                                        exception.getStatusCode().value(),
                                        exception.getResponseBodyAsString(),
                                        exception
                                )
                        )
                        .doOnError(
                                exception -> !(exception instanceof WebClientResponseException),
                                exception -> log.error(
                                        "[면접 결과 저장 실패] sessionId={}, interviewId={}, API 서버 통신 오류={}",
                                        request.getSessionId(),
                                        request.getInterviewId(),
                                        exception.getMessage(),
                                        exception
                                )
                        )
                        .then()
        );
    }
}
