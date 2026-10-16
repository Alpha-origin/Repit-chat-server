package com.person.repit.interview.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.dto.response.ChatQuestionResponse;
import com.person.repit.interview.service.ChatInterviewService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.reactivestreams.Publisher;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.web.reactive.socket.CloseStatus;
import org.springframework.web.reactive.socket.HandshakeInfo;
import org.springframework.web.reactive.socket.WebSocketMessage;
import org.springframework.web.reactive.socket.WebSocketSession;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReactiveChatInterviewWebSocketHandlerTest {
    private ChatInterviewService service;
    private WebSocketSession session;
    private ReactiveChatInterviewWebSocketHandler handler;
    private final List<String> outbound = new ArrayList<>();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        service = mock(ChatInterviewService.class);
        session = mock(WebSocketSession.class);
        handler = new ReactiveChatInterviewWebSocketHandler(
                new ObjectMapper(), service, new RepitMetrics(new SimpleMeterRegistry()));
        when(session.getHandshakeInfo()).thenReturn(new HandshakeInfo(
                URI.create("ws://localhost/chat?sessionId=s"), new HttpHeaders(), Mono.empty(), null));
        when(service.getCurrentQuestion("s")).thenReturn(Mono.just(
                ChatQuestionResponse.builder().questionId(1L).content("질문").build()));
        when(service.submitAnswer(eq("s"), any())).thenReturn(Mono.just(
                ChatProgressResponse.next(ChatQuestion.builder().questionId(2L).content("다음 질문").build())));
        when(session.textMessage(anyString())).thenAnswer(call -> textMessage(call.getArgument(0)));
        when(session.send(any())).thenAnswer(call -> Flux.from(
                        (Publisher<WebSocketMessage>) call.getArgument(0))
                .doOnNext(message -> outbound.add(message.getPayloadAsText())).then());
        when(session.close(any())).thenReturn(Mono.empty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{", "{}"})
    void 잘못된_메시지_다음에도_정상_답변을_처리한다(String payload) {
        when(session.receive()).thenReturn(Flux.just(textMessage(payload), textMessage(answer(0))));

        StepVerifier.create(handler.handle(session)).verifyComplete();

        assertThat(outbound).hasSize(3);
        assertThat(outbound.get(1)).contains("\"type\":\"ERROR\"");
        assertThat(outbound.get(2)).contains("\"type\":\"QUESTION\"", "다음 질문");
        verify(service).submitAnswer(eq("s"), any());
        verify(session).close(CloseStatus.NORMAL);
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, Integer.MIN_VALUE})
    void 음수_응답_시간은_거절하고_이후_정상_답변만_전달한다(int responseTime) {
        when(session.receive()).thenReturn(Flux.just(
                textMessage(answer(responseTime)), textMessage(answer(10))));

        StepVerifier.create(handler.handle(session)).verifyComplete();

        assertThat(outbound).hasSize(3);
        assertThat(outbound.get(1)).contains("\"type\":\"ERROR\"", "0 이상");
        ArgumentCaptor<ChatAnswerRequest> captured = ArgumentCaptor.forClass(ChatAnswerRequest.class);
        verify(service).submitAnswer(eq("s"), captured.capture());
        assertThat(captured.getValue().getResponseTime()).isEqualTo(10);
        verify(session).close(CloseStatus.NORMAL);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void 음수가_아닌_응답_시간은_그대로_전달한다(int responseTime) {
        when(session.receive()).thenReturn(Flux.just(textMessage(answer(responseTime))));

        StepVerifier.create(handler.handle(session)).verifyComplete();

        assertThat(outbound).hasSize(2);
        assertThat(outbound.get(1)).contains("\"type\":\"QUESTION\"");
        ArgumentCaptor<ChatAnswerRequest> captured = ArgumentCaptor.forClass(ChatAnswerRequest.class);
        verify(service).submitAnswer(eq("s"), captured.capture());
        assertThat(captured.getValue().getResponseTime()).isEqualTo(responseTime);
    }

    private String answer(int responseTime) {
        return """
                {"type":"ANSWER","questionId":1,"responseTime":%d,"content":"답변"}
                """.formatted(responseTime);
    }

    private WebSocketMessage textMessage(String payload) {
        return new WebSocketMessage(WebSocketMessage.Type.TEXT,
                DefaultDataBufferFactory.sharedInstance.wrap(payload.getBytes(StandardCharsets.UTF_8)));
    }
}
