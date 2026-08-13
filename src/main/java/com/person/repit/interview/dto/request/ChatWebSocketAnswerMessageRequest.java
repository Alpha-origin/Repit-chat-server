package com.person.repit.interview.dto.request;

import com.person.repit.common.type.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChatWebSocketAnswerMessageRequest {
    @NotNull
    private MessageType type;

    @NotNull
    private Long questionId;

    @NotNull
    private Integer responseTime;

    @NotBlank
    private String content;

    public ChatAnswerRequest toChatAnswerRequest() {
        return ChatAnswerRequest.builder()
                .questionId(questionId)
                .responseTime(responseTime)
                .content(content)
                .build();
    }
}
