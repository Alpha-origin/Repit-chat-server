package com.person.repit.interview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.service.AiRequestConcurrencyLimiter;
import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.InterviewType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiQuestionClientResponseTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "null", "{}", "{\"required\":true}",
            "{\"required\":true,\"content\":null,\"expectedAnswer\":\"답안\"}",
            "{\"required\":true,\"content\":\"\",\"expectedAnswer\":\"답안\"}",
            "{\"required\":true,\"content\":\"   \",\"expectedAnswer\":\"답안\"}",
            "{\"required\":true,\"content\":\"질문\"}",
            "{\"required\":true,\"content\":\"질문\",\"expectedAnswer\":null}",
            "{\"required\":true,\"content\":\"질문\",\"expectedAnswer\":\"\"}",
            "{\"required\":true,\"content\":\"질문\",\"expectedAnswer\":\" \\n\\t\"}"
    })
    void 불완전한_AI_응답은_꼬리질문을_생성하지_않는다(String text) throws Exception {
        StepVerifier.create(client(text).decideFollowQuestion(request()))
                .assertNext(response -> {
                    assertThat(response.getRequired()).isFalse();
                    assertThat(response.getContent()).isNull();
                    assertThat(response.getExpectedAnswer()).isNull();
                })
                .verifyComplete();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 정상_AI_응답은_판단과_내용을_유지한다(boolean required) throws Exception {
        String text = """
                {"score":2,"required":%s,"intention":"선택 근거 확인",
                 "content":"왜 선택했나요?","spokenText":"왜 선택했나요?","expectedAnswer":"선택 근거"}
                """.formatted(required);

        StepVerifier.create(client(text).decideFollowQuestion(request()))
                .assertNext(response -> {
                    assertThat(response.getRequired()).isEqualTo(required);
                    assertThat(response.getScore()).isEqualTo(2);
                    assertThat(response.getContent()).isEqualTo("왜 선택했나요?");
                    assertThat(response.getExpectedAnswer()).isEqualTo("선택 근거");
                    assertThat(response.getIntention()).isEqualTo("선택 근거 확인");
                    assertThat(response.getSpokenText()).isEqualTo("왜 선택했나요?");
                })
                .verifyComplete();
    }

    private AiQuestionClientImpl client(String text) throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        String body = mapper.writeValueAsString(Map.of("content", List.of(Map.of("type", "text", "text", text))));
        WebClient webClient = WebClient.builder().baseUrl("http://localhost")
                .exchangeFunction(request -> Mono.just(ClientResponse.create(HttpStatus.OK)
                        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE).body(body).build()))
                .build();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        return new AiQuestionClientImpl(mapper, "stub", 800, webClient,
                new RepitMetrics(registry), new AiRequestConcurrencyLimiter(1, registry));
    }

    private FollowQuestionAiRequest request() {
        return FollowQuestionAiRequest.builder().sessionId("s").interviewId(1L).userId(2L).personaId(3L)
                .personality(InterviewType.FRIENDLY).tone(InterviewTone.GENTLE)
                .questionId(4L).questionContent("질문").expectedAnswer("답안").answerContent("답변")
                .responseTime(10).build();
    }
}
