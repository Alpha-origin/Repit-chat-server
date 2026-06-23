package com.person.repit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.interview.dto.request.ChatInterviewResultSaveRequest;
import com.person.repit.interview.dto.response.MockInterviewResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.UUID;

@Component
public class ApiServerClient {

    private final RestClient restClient;

    public ApiServerClient(
            @Value("${repit.api-server.base-url}") String baseUrl
    ) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public MockInterviewResponse getMockInterview(UUID jobId) {

        String rawResponse =
                restClient.get()
                        .uri("/api/v1/ai?jobId={jobId}", jobId)
                        .retrieve()
                        .body(String.class);

        System.out.println("========== RAW RESPONSE ==========");
        System.out.println(rawResponse);

        ObjectMapper mapper = new ObjectMapper();

        try {
            return mapper.readValue(
                    rawResponse,
                    MockInterviewResponse.class
            );
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void saveInterviewResult(
            ChatInterviewResultSaveRequest request,
            String authorization
    ) {

        restClient.post()
                .uri("/api/interviews/result")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .header("Authorization", authorization)
                .retrieve()
                .toBodilessEntity();
    }
}
