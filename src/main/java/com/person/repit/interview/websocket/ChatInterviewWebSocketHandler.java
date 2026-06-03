package com.person.repit.interview.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.common.type.MessageType;
import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatWebSocketMessageRequest;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.dto.response.ChatQuestionResponse;
import com.person.repit.interview.dto.response.ChatWebSocketMessageResponse;
import com.person.repit.interview.service.ChatInterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;
import java.net.URI;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChatInterviewWebSocketHandler extends TextWebSocketHandler {

    private static final String SESSION_ID = "sessionId";

    private final ObjectMapper objectMapper;
    private final ChatInterviewService chatInterviewService;

    private final Map<String, String> sessionIdMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);

        if (sessionId == null || sessionId.isBlank()) {
            send(session, ChatWebSocketMessageResponse.error("sessionId가 필요합니다."));
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        try {
            ChatQuestionResponse question = chatInterviewService.getCurrentQuestion(sessionId);
            sessionIdMap.put(session.getId(), sessionId);
            send(session, ChatWebSocketMessageResponse.question(question));
        } catch (RuntimeException exception) {
            send(session, ChatWebSocketMessageResponse.error(exception.getMessage()));
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String sessionId = sessionIdMap.get(session.getId());

        if (sessionId == null) {
            send(session, ChatWebSocketMessageResponse.error("유효하지 않은 웹소켓 세션입니다."));
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        ChatWebSocketMessageRequest request =
                objectMapper.readValue(message.getPayload(), ChatWebSocketMessageRequest.class);

        if (request.getType() == MessageType.ANSWER) {
            ChatAnswerRequest answerRequest = ChatAnswerRequest.from(request);
            ChatProgressResponse progress = chatInterviewService.submitAnswer(sessionId, answerRequest);

            send(session, ChatWebSocketMessageResponse.progress(progress));

            if (progress.getQuestion() == null) {
                session.close(CloseStatus.NORMAL);
            }

            return;
        }

        if (request.getType() == MessageType.COMPLETE) {
            ChatProgressResponse progress = chatInterviewService.completeInterview(sessionId);
            send(session, ChatWebSocketMessageResponse.progress(progress));
            session.close(CloseStatus.NORMAL);
            return;
        }

        if (request.getType() == MessageType.QUIT) {
            ChatProgressResponse progress = chatInterviewService.quitInterview(sessionId);
            send(session, ChatWebSocketMessageResponse.end(progress.getMessage()));
            session.close(CloseStatus.NORMAL);
            return;
        }

        send(session, ChatWebSocketMessageResponse.error("지원하지 않는 메시지 타입입니다."));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessionIdMap.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        send(session, ChatWebSocketMessageResponse.error("웹소켓 통신 중 오류가 발생했습니다."));
        session.close(CloseStatus.SERVER_ERROR);
    }

    private String extractSessionId(WebSocketSession session) {
        URI uri = session.getUri();

        if (uri == null) {
            return null;
        }

        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst(SESSION_ID);
    }

    private void send(WebSocketSession session, Object payload) throws IOException {
        if (session.isOpen()) {
            session.sendMessage(new TextMessage(objectMapper.writeValueAsString(payload)));
        }
    }
}