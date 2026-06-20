package com.person.repit.interview.service;

import com.person.repit.interview.dto.request.ChatInterviewResultSaveRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Component
public class ApiServerClient {
    private final RestClient restClient;

    public ApiServerClient(@Value("${repit.api-server.base-url}") String baseUrl) {
        System.out.println("API URL = " + baseUrl);
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public void saveInterviewResult(ChatInterviewResultSaveRequest request, String authorization) {

        try {
            ObjectMapper mapper = new ObjectMapper();

            System.out.println("===== REQUEST JSON =====");
            System.out.println(
                    mapper.writerWithDefaultPrettyPrinter()
                            .writeValueAsString(request)
            );
        } catch (Exception e) {
            e.printStackTrace();
        }

        restClient.post()
                .uri("/api/interviews/result")
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .header("Authorization", authorization)
                .retrieve()
                .toBodilessEntity();
    }
}
