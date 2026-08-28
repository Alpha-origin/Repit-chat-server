package com.person.repit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.dto.response.MockInterviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.UUID;

@Slf4j
@Component
public class ApiServerClient {

    private final RestClient restClient;
    private final String baseUrl;
    private final ObjectMapper objectMapper;

    public ApiServerClient(
            @Value("${repit.api-server.base-url}") String baseUrl,
            ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.objectMapper = objectMapper;

        log.info("API SERVER URL = {}", baseUrl);

        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public MockInterviewResponse getMockInterview(
            UUID jobId,
            String authorization
    ) {

        log.info("AI REQUEST jobId={}", jobId);

        log.info(
                "REQUEST URL = {}/api/v1/ai?jobId={}",
                baseUrl,
                jobId
        );

        String rawResponse =
                restClient.get()
                        .uri("/api/v1/ai?jobId={jobId}", jobId)
                        .header("Authorization", authorization)
                        .retrieve()
                        .body(String.class);

        try {
            return objectMapper.readValue(
                    rawResponse,
                    MockInterviewResponse.class
            );
        } catch (Exception e) {
            log.error("JSON PARSE ERROR", e);
            throw new RuntimeException(e);
        }
    }

    public void saveInterviewResult(
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

        try {
            ResponseEntity<Void> response = restClient.post()
                    .uri("/api/interviews/result")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

            log.info(
                    "[면접 결과 저장 성공] sessionId={}, interviewId={}, HTTP 상태={}",
                    request.getSessionId(),
                    request.getInterviewId(),
                    response.getStatusCode().value()
            );
        } catch (RestClientResponseException exception) {
            log.error(
                    "[면접 결과 저장 실패] sessionId={}, interviewId={}, HTTP 상태={}, 응답={}",
                    request.getSessionId(),
                    request.getInterviewId(),
                    exception.getStatusCode().value(),
                    exception.getResponseBodyAsString(),
                    exception
            );
            throw exception;
        } catch (RestClientException exception) {
            log.error(
                    "[면접 결과 저장 실패] sessionId={}, interviewId={}, API 서버 통신 오류={}",
                    request.getSessionId(),
                    request.getInterviewId(),
                    exception.getMessage(),
                    exception
            );
            throw exception;
        }
    }
}
