package com.person.repit.interview.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.person.repit.common.type.MessageType;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatWebSocketMessageRequest {
    @NotNull
    private MessageType type;
}
