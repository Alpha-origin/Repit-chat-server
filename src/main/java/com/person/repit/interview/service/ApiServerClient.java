package com.person.repit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.interview.dto.request.ChatInterviewResultSaveRequest;
import com.person.repit.interview.dto.response.MockInterviewResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

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
            ChatInterviewResultSaveRequest request
    ) {

        restClient.post()
                .uri("/api/interviews/result")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .retrieve()
                .toBodilessEntity();
    }
}
