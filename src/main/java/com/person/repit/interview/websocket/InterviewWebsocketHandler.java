package com.person.repit.interview.websocket;

import com.person.repit.interview.dto.request.MessageRequest;
import com.person.repit.common.type.MessageType;
import com.person.repit.interview.dto.response.MessageResponse;
import com.person.repit.interview.dto.response.QuestionResponse;
import com.person.repit.interview.service.InterviewService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.ObjectMapper;


@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewWebsocketHandler extends TextWebSocketHandler {

    private final ObjectMapper objectMapper;
    private final InterviewService interviewService;

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {

        MessageRequest request = objectMapper.readValue(message.getPayload(), MessageRequest.class);

        if (request.getType() == MessageType.START) {
            String json = objectMapper.writeValueAsString(request.getData());
            StartInterviewRequest startRequest = objectMapper.readValue(json, StartInterviewRequest.class);
            QuestionResponse questionResponse = interviewService.startInterview(startRequest);
            MessageResponse response = MessageResponse
                    .builder()
                    .type(MessageType.QUESTION)
                    .data(questionResponse)
                    .build();
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(response)));
        }
    }
}
