package com.person.repit.interview.dto.request;

import com.person.repit.common.type.MessageType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatWebSocketMessageRequest {

    @NotNull
    private MessageType type;

    private Long questionId;

    private Integer responseTime;

    private String content;
}
