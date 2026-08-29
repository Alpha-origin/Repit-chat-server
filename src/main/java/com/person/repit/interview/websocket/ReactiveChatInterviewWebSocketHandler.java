package com.person.repit.interview.websocket;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.common.type.MessageType;
import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatWebSocketAnswerMessageRequest;
import com.person.repit.interview.dto.request.ChatWebSocketMessageRequest;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.dto.response.ChatWebSocketMessageResponse;
import com.person.repit.interview.service.ChatInterviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.WebSocketHandler;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Component
@RequiredArgsConstructor
public class ReactiveChatInterviewWebSocketHandler implements WebSocketHandler {

    private static final String SESSION_ID = "sessionId";

    private final ObjectMapper objectMapper;
    private final ChatInterviewService chatInterviewService;
    private final RepitMetrics metrics;

    @Override
    public Mono<Void> handle(WebSocketSession session) {
        String sessionId = extractSessionId(session);
        AtomicBoolean connected = new AtomicBoolean(false);
        AtomicReference<CloseStatus> closeStatus = new AtomicReference<>(CloseStatus.NORMAL);

        Flux<OutboundMessage> outbound = sessionId == null || sessionId.isBlank()
                ? Flux.just(error("sessionId가 필요합니다.", CloseStatus.BAD_DATA))
                : createMessageFlow(session, sessionId, connected);

        Flux<WebSocketMessage> messages = outbound
                .doOnNext(message -> {
                    if (message.closeStatus() != null) {
                        closeStatus.set(message.closeStatus());
                    }
                })
                .takeUntil(message -> message.closeStatus() != null)
                .map(message -> session.textMessage(serialize(message.payload())));

        return session.send(messages)
                .then(Mono.defer(() -> session.close(closeStatus.get())))
                .onErrorResume(exception -> session.close(CloseStatus.SERVER_ERROR))
                .doFinally(signal -> {
                    if (connected.compareAndSet(true, false)) {
                        metrics.webSocketDisconnected();
                    }
                });
    }

    private Flux<OutboundMessage> createMessageFlow(
            WebSocketSession session,
            String sessionId,
            AtomicBoolean connected
    ) {
        Mono<OutboundMessage> initialQuestion = chatInterviewService.getCurrentQuestion(sessionId)
                .doOnSuccess(question -> {
                    connected.set(true);
                    metrics.webSocketConnected();
                })
                .map(question -> keepOpen(ChatWebSocketMessageResponse.question(question)))
                .onErrorResume(exception -> Mono.just(error(messageOf(exception), CloseStatus.BAD_DATA)));

        Flux<OutboundMessage> responses = session.receive()
                .concatMap(message -> processMessage(sessionId, message.getPayloadAsText()))
                .onErrorResume(exception -> Mono.just(error(messageOf(exception), CloseStatus.SERVER_ERROR)));

        return Flux.concat(initialQuestion, responses);
    }

    private Mono<OutboundMessage> processMessage(String sessionId, String payload) {
        metrics.webSocketMessageReceived();

        ChatWebSocketMessageRequest request;
        try {
            request = objectMapper.readValue(payload, ChatWebSocketMessageRequest.class);
        } catch (Exception exception) {
            return Mono.just(error("메시지 형식이 올바르지 않습니다."));
        }

        if (request.getType() == null) {
            return Mono.just(error("메시지 타입이 필요합니다."));
        }

        return switch (request.getType()) {
            case ANSWER -> processAnswer(sessionId, payload);
            case COMPLETE -> processCompletion(sessionId);
            case QUIT -> processQuit(sessionId);
            default -> Mono.just(error("지원하지 않는 메시지 타입입니다."));
        };
    }

    private Mono<OutboundMessage> processAnswer(String sessionId, String payload) {
        ChatWebSocketAnswerMessageRequest message;
        try {
            message = objectMapper.readValue(payload, ChatWebSocketAnswerMessageRequest.class);
        } catch (Exception exception) {
            return Mono.just(error("답변 메세지 형식이 올바르지 않습니다."));
        }

        if (message.getQuestionId() == null) {
            return Mono.just(error("질문 ID가 존재하지 않습니다."));
        }
        if (message.getResponseTime() == null) {
            return Mono.just(error("답변 응답 시간이 존재하지 않습니다."));
        }
        if (message.getContent() == null || message.getContent().isBlank()) {
            return Mono.just(error("답변 내용이 존재하지 않습니다."));
        }

        ChatAnswerRequest answerRequest = message.toChatAnswerRequest();

        return metrics.recordAnswerProcessing(
                        chatInterviewService.submitAnswer(sessionId, answerRequest)
                )
                .map(progress -> progressMessage(progress, progress.getQuestion() == null))
                .doOnSuccess(ignored -> metrics.webSocketMessageProcessed());
    }

    private Mono<OutboundMessage> processCompletion(String sessionId) {
        return chatInterviewService.completeInterview(sessionId)
                .map(progress -> progressMessage(progress, true))
                .doOnSuccess(ignored -> metrics.webSocketMessageProcessed());
    }

    private Mono<OutboundMessage> processQuit(String sessionId) {
        return chatInterviewService.quitInterview(sessionId)
                .map(progress -> closeNormally(ChatWebSocketMessageResponse.end(progress.getMessage())))
                .doOnSuccess(ignored -> metrics.webSocketMessageProcessed());
    }

    private OutboundMessage progressMessage(ChatProgressResponse progress, boolean close) {
        ChatWebSocketMessageResponse response = ChatWebSocketMessageResponse.progress(progress);
        return close ? closeNormally(response) : keepOpen(response);
    }

    private String extractSessionId(WebSocketSession session) {
        URI uri = session.getHandshakeInfo().getUri();
        return UriComponentsBuilder.fromUri(uri)
                .build()
                .getQueryParams()
                .getFirst(SESSION_ID);
    }

    private String serialize(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("WebSocket 응답 직렬화에 실패했습니다.", exception);
        }
    }

    private OutboundMessage keepOpen(Object payload) {
        return new OutboundMessage(payload, null);
    }

    private OutboundMessage closeNormally(Object payload) {
        return new OutboundMessage(payload, CloseStatus.NORMAL);
    }

    private OutboundMessage error(String message) {
        return error(message, null);
    }

    private OutboundMessage error(String message, CloseStatus closeStatus) {
        metrics.webSocketMessageFailed();
        return new OutboundMessage(ChatWebSocketMessageResponse.error(message), closeStatus);
    }

    private String messageOf(Throwable throwable) {
        return throwable.getMessage() == null
                ? "웹소켓 메시지 처리 중 오류가 발생했습니다."
                : throwable.getMessage();
    }

    private record OutboundMessage(Object payload, CloseStatus closeStatus) {
    }
}
