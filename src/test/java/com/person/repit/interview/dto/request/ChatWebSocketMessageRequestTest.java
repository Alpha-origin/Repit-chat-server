package com.person.repit.interview.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.common.type.MessageType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ChatWebSocketMessageRequestTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void readsAnswerPayloadAsGenericWebSocketMessage() throws Exception {
        String payload = """
                {
                  "type": "ANSWER",
                  "questionId": 1,
                  "responseTime": 10,
                  "content": "테스트 답변"
                }
                """;

        ChatWebSocketMessageRequest request = objectMapper.readValue(
                payload,
                ChatWebSocketMessageRequest.class
        );

        assertThat(request.getType()).isEqualTo(MessageType.ANSWER);
    }
}
