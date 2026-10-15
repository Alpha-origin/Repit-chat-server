package com.person.repit.interview.dto.request;

import com.person.repit.common.exception.handler.GlobalExceptionHandler;
import com.person.repit.interview.controller.ChatInterviewController;
import com.person.repit.interview.service.ChatInterviewService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.util.stream.Stream;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ChatInterviewPrepareRequestTest {

    private ChatInterviewService service;
    private WebTestClient client;

    @BeforeEach
    void setUp() {
        service = mock(ChatInterviewService.class);
        when(service.prepareInterview(any())).thenReturn(Mono.empty());
        client = WebTestClient.bindToController(new ChatInterviewController(service))
                .controllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void 기업이_없거나_null이면_기존_요청을_허용한다() {
        for (String company : new String[]{"", ",\"company\":null"}) {
            client.post().uri("/chat/interviews").contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(request(company)).exchange().expectStatus().isOk();
        }
        verify(service, times(2)).prepareInterview(any());
    }

    @Test
    void 이름_30자와_요약_600자까지_허용하고_알수없는_필드는_무시한다() {
        client.post().uri("/chat/interviews").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request(",\"company\":{\"displayName\":\"" + "가".repeat(30)
                        + "\",\"interviewerBrief\":\"" + "나".repeat(600)
                        + "\",\"extra\":true}"))
                .exchange().expectStatus().isOk();
        verify(service).prepareInterview(any());
    }

    @ParameterizedTest
    @MethodSource("invalidCompanies")
    void 잘못된_기업_정보는_400과_필드_오류를_반환한다(String company, String field) {
        client.post().uri("/chat/interviews").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request(",\"company\":" + company)).exchange()
                .expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors['company." + field + "']").exists();
        verify(service, never()).prepareInterview(any());
    }

    private static Stream<Arguments> invalidCompanies() {
        return Stream.of(
                Arguments.of("{}", "displayName"),
                Arguments.of("{\"interviewerBrief\":\"요약\"}", "displayName"),
                Arguments.of(company("", "요약"), "displayName"),
                Arguments.of(company("   ", "요약"), "displayName"),
                Arguments.of(company("가".repeat(31), "요약"), "displayName"),
                Arguments.of("{\"displayName\":\"샘플기업\"}", "interviewerBrief"),
                Arguments.of(company("샘플기업", ""), "interviewerBrief"),
                Arguments.of(company("샘플기업", "   "), "interviewerBrief"),
                Arguments.of(company("샘플기업", "나".repeat(601)), "interviewerBrief")
        );
    }

    private static String company(String name, String brief) {
        return "{\"displayName\":\"" + name + "\",\"interviewerBrief\":\"" + brief + "\"}";
    }

    private String request(String company) {
        return """
                {"sessionId":"s","interviewId":1,"userId":2,"status":"IN_PROGRESS",
                 "mode":"SOLO","personality":"FRIENDLY","tone":"GENTLE",
                 "major":"BACKEND","level":"NORMAL",
                 "questions":[{"id":1,"question":"질문","personaId":3}]%s}
                """.formatted(company);
    }
}
