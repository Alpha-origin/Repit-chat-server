package com.person.repit.interview.dto.request;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
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

import static org.assertj.core.api.Assertions.assertThat;
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

    @Test
    void 기업_정보는_앞뒤_공백을_제거하고_유니코드_문자수로_검사한다() {
        client.post().uri("/chat/interviews").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request(",\"company\":" + company("  " + "😀".repeat(30) + "  ",
                        "  " + "😀".repeat(600) + "  ")))
                .exchange().expectStatus().isOk();
        var captured = org.mockito.ArgumentCaptor.forClass(ChatInterviewPrepareRequest.class);
        verify(service).prepareInterview(captured.capture());
        assertThat(captured.getValue().getCompany().getDisplayName()).isEqualTo("😀".repeat(30));
        assertThat(captured.getValue().getCompany().getInterviewerBrief()).isEqualTo("😀".repeat(600));
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

    @ParameterizedTest
    @MethodSource("nullListElements")
    void 배열_안의_null은_서비스_호출_전에_400으로_거절한다(
            String field, String elements, String errorField
    ) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode body = (ObjectNode) mapper.readTree(request(""));
        body.set(field, mapper.readTree(elements));

        client.post().uri("/chat/interviews").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(body.toString()).exchange().expectStatus().isBadRequest().expectBody()
                .jsonPath("$.code").isEqualTo("VALIDATION_FAILED")
                .jsonPath("$.errors['" + errorField + "']").exists();
        verify(service, never()).prepareInterview(any());
    }

    @Test
    void 면접관_목록이_null인_기존_요청도_허용한다() {
        client.post().uri("/chat/interviews").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(request(",\"personas\":null")).exchange().expectStatus().isOk();
        verify(service).prepareInterview(any());
    }

    private static Stream<Arguments> nullListElements() {
        String question = "{\"id\":1,\"question\":\"질문\",\"personaId\":3}";
        String persona = """
                {"personaId":3,"role":"HR","type":"FRIENDLY","tone":"GENTLE","level":"NORMAL"}
                """;
        return Stream.of(
                Arguments.of("questions", "[null]", "questions[0]"),
                Arguments.of("questions", "[" + question + ",null]", "questions[1]"),
                Arguments.of("personas", "[null]", "personas[0]"),
                Arguments.of("personas", "[" + persona + ",null]", "personas[1]")
        );
    }

    private static Stream<Arguments> invalidCompanies() {
        return Stream.of(
                Arguments.of("{}", "displayName"),
                Arguments.of("{\"interviewerBrief\":\"요약\"}", "displayName"),
                Arguments.of(company("", "요약"), "displayName"),
                Arguments.of(company("   ", "요약"), "displayName"),
                Arguments.of(company("가".repeat(31), "요약"), "displayName"),
                Arguments.of(company("😀".repeat(31), "요약"), "displayName"),
                Arguments.of("{\"displayName\":\"샘플기업\"}", "interviewerBrief"),
                Arguments.of(company("샘플기업", ""), "interviewerBrief"),
                Arguments.of(company("샘플기업", "   "), "interviewerBrief"),
                Arguments.of(company("샘플기업", "나".repeat(601)), "interviewerBrief"),
                Arguments.of(company("샘플기업", "😀".repeat(601)), "interviewerBrief")
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
