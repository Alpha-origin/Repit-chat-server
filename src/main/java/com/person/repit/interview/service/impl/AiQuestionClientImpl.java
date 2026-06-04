package com.person.repit.interview.service.impl;

import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
import com.person.repit.interview.service.AiQuestionClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class AiQuestionClientImpl implements AiQuestionClient {

    private final RestClient restClient;

    public AiQuestionClientImpl(@Value("${repit.ai-server.base-url}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    @Override
    public FollowQuestionAiResponse decideFollowQuestion(FollowQuestionAiRequest request) {
        try {
            FollowQuestionAiResponse response = restClient.post()
                    .uri("/api/ai/follow-question")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(FollowQuestionAiResponse.class);

            if (response == null) {
                return FollowQuestionAiResponse.notRequired();
            }

            return response;
        } catch (RestClientException exception) {
            return FollowQuestionAiResponse.notRequired();
        }
    }
}