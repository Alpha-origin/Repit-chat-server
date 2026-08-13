package com.person.repit.interview.dto.request;

import com.person.repit.common.type.MessageType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatWebSocketMessageRequest {

    @NotNull
    private MessageType type;

    @NotNull
    private Long questionId;

    @NotNull
    private Integer responseTime;

    @NotBlank
    private String content;
}
