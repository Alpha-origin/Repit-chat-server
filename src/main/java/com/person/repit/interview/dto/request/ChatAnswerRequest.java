package com.person.repit.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAnswerRequest {

    @NotNull
    private Long questionId;

    private Integer responseTime;

    @NotBlank
    private String content;

    public static ChatAnswerRequest from(ChatWebSocketMessageRequest request) {
        return ChatAnswerRequest.builder()
                .questionId(request.getQuestionId())
                .responseTime(request.getResponseTime())
                .content(request.getContent())
                .build();
    }
}