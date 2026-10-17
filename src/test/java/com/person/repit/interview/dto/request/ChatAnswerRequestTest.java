package com.person.repit.interview.dto.request;

import com.person.repit.common.exception.handler.GlobalExceptionHandler;
import com.person.repit.interview.controller.ChatInterviewController;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.service.ChatInterviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChatAnswerRequestTest {
    private ChatInterviewService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(ChatInterviewService.class);
        when(service.submitAnswer(eq("s"), any())).thenReturn(Mono.just(ChatProgressResponse.end()));
        client = WebTestClient.bindToController(new ChatInterviewController(service))
                .controllerAdvice(new GlobalExceptionHandler()).build();
    }

    @ParameterizedTest
    @ValueSource(strings = {"-1", "-2147483648", "null"})
    void 음수이거나_없는_응답_시간은_서비스_호출_전에_거절한다(String responseTime) {
        client.post().uri("/chat/interviews/s/answers").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(answer(responseTime)).exchange()
                .expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors.responseTime").exists();

        verify(service, never()).submitAnswer(any(), any());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, Integer.MAX_VALUE})
    void 음수가_아닌_응답_시간은_허용한다(int responseTime) {
        client.post().uri("/chat/interviews/s/answers").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(answer(Integer.toString(responseTime))).exchange().expectStatus().isOk();

        verify(service).submitAnswer(eq("s"), any());
    }

    private String answer(String responseTime) {
        return """
                {"questionId":1,"responseTime":%s,"content":"답변"}
                """.formatted(responseTime);
    }
}
