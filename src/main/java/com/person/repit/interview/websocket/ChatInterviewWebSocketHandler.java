package com.person.repit.interview.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.common.type.MessageType;
import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatWebSocketAnswerMessageRequest;
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
    private final RepitMetrics metrics;

    private final Map<String, String> sessionIdMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String sessionId = extractSessionId(session);

        if (sessionId == null || sessionId.isBlank()) {
            sendError(session, "sessionId가 필요합니다.");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        try {
            ChatQuestionResponse question = chatInterviewService.getCurrentQuestion(sessionId);
            sessionIdMap.put(session.getId(), sessionId);
            metrics.webSocketConnected();
            send(session, ChatWebSocketMessageResponse.question(question));
        } catch (RuntimeException exception) {
            sendError(session, exception.getMessage());
            session.close(CloseStatus.BAD_DATA);
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        metrics.webSocketMessageReceived();
        String sessionId = sessionIdMap.get(session.getId());

        if (sessionId == null) {
            sendError(session, "현재 연결에 등록된 면접 세션이 없습니다.");
            session.close(CloseStatus.BAD_DATA);
            return;
        }

        ChatWebSocketMessageRequest request;

        try {
            request = objectMapper.readValue(
                    message.getPayload(),
                    ChatWebSocketMessageRequest.class
            );
        } catch (Exception exception) {
            sendError(session, "메시지 형식이 올바르지 않습니다.");
            return;
        }

        if (request.getType() == null) {
            sendError(session, "메시지 타입이 필요합니다.");
            return;
        }

        if (request.getType() == MessageType.ANSWER) {
            ChatWebSocketAnswerMessageRequest answerMessageRequest;

            try {
                answerMessageRequest = objectMapper.readValue(
                        message.getPayload(),
                        ChatWebSocketAnswerMessageRequest.class
                );
            } catch (Exception exception) {
                sendError(session, "답변 메세지 형식이 올바르지 않습니다.");
                return;
            }

            if (answerMessageRequest.getQuestionId() == null) {
                sendError(session, "질문 ID가 존재하지 않습니다.");
                return;
            }

            if (answerMessageRequest.getResponseTime() == null) {
                sendError(session, "답변 응답 시간이 존재하지 않습니다.");
                return;
            }

            if (answerMessageRequest.getContent() == null) {
                sendError(session, "답변 내용이 존재하지 않습니다.");
                return;
            }

            if (answerMessageRequest.getContent().isBlank()) {
                sendError(session, "답변 내용이 존재하지 않습니다.");
                return;
            }

            ChatAnswerRequest answerRequest = answerMessageRequest.toChatAnswerRequest();

            ChatProgressResponse progress = metrics.recordAnswerProcessing(
                    () -> chatInterviewService.submitAnswer(sessionId, answerRequest)
            );

            send(session, ChatWebSocketMessageResponse.progress(progress));
            metrics.webSocketMessageProcessed();

            if (progress.getQuestion() == null) {
                session.close(CloseStatus.NORMAL);
            }

            return;
        }

        if (request.getType() == MessageType.COMPLETE) {
            ChatProgressResponse progress = chatInterviewService.completeInterview(sessionId);
            send(session, ChatWebSocketMessageResponse.progress(progress));
            metrics.webSocketMessageProcessed();
            session.close(CloseStatus.NORMAL);
            return;
        }

        if (request.getType() == MessageType.QUIT) {
            ChatProgressResponse progress = chatInterviewService.quitInterview(sessionId);
            send(session, ChatWebSocketMessageResponse.end(progress.getMessage()));
            metrics.webSocketMessageProcessed();
            session.close(CloseStatus.NORMAL);
            return;
        }

        sendError(session, "지원하지 않는 메시지 타입입니다.");
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        if (sessionIdMap.remove(session.getId()) != null) {
            metrics.webSocketDisconnected();
        }
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        sendError(session, "웹소켓 통신 중 오류가 발생했습니다.");
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

    private void sendError(WebSocketSession session, String message) throws IOException {
        metrics.webSocketMessageFailed();
        send(session, ChatWebSocketMessageResponse.error(message));
    }
}
