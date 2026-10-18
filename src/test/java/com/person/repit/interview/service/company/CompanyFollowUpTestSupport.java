package com.person.repit.interview.service.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.service.AiRequestConcurrencyLimiter;
import com.person.repit.interview.service.impl.AiQuestionClientImpl;
import com.person.repit.interview.type.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.reactive.MockClientHttpRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.IntFunction;

final class CompanyFollowUpTestSupport {
    static final ObjectMapper MAPPER = new ObjectMapper();
    static final String BASE = """
            {"score":2,"required":true,"intention":"선택 근거 확인",
             "content":"일반 질문?","spokenText":"일반 질문?","expectedAnswer":"일반 답안"}
            """;
    static final String COMPANY = """
            {"intention":"검증 근거를 설명할 수 있는지","content":"기업 관점 질문?",
             "spokenText":"기업 관점 질문?","expectedAnswer":"실제로 확인한 근거"}
            """;

    static CompanyFollowUpSettings enabled() {
        return new CompanyFollowUpSettings(true, Duration.ofSeconds(5), Duration.ofSeconds(2), 800);
    }

    static FollowQuestionAiRequest request(String name) {
        return FollowQuestionAiRequest.builder().sessionId("s").interviewId(1L).userId(2L).personaId(3L)
                .personality(InterviewType.FRIENDLY).tone(InterviewTone.GENTLE).role(InterviewRole.TECH)
                .major(InterviewMajor.BACKEND).level(InterviewLevel.NORMAL)
                .questionId(4L).questionCategory("implementation").questionIntention("중복 처리 검증 근거")
                .questionContent("어떻게 검증했나요?").expectedAnswer("검증 근거")
                .answerContent("구현했습니다").responseTime(10)
                .companyName(name).companyBrief("주문 동시성과 고객 관점").build();
    }

    static ClientResponse response(String text) {
        return response(text, "end_turn");
    }

    static ClientResponse response(String text, String stop) {
        try {
            String body = MAPPER.writeValueAsString(Map.of("content", List.of(Map.of("type", "text", "text", text)),
                    "stop_reason", stop, "usage", Map.of("input_tokens", 11, "output_tokens", 17)));
            return ClientResponse.create(HttpStatus.OK).header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                    .body(body).build();
        } catch (Exception error) {
            throw new AssertionError(error);
        }
    }

    static Harness harness(CompanyFollowUpSettings settings, IntFunction<Mono<ClientResponse>> replies) {
        List<JsonNode> bodies = new ArrayList<>();
        WebClient webClient = WebClient.builder().baseUrl("http://localhost")
                .exchangeFunction(request -> {
                    var output = new MockClientHttpRequest(request.method(), request.url());
                    return request.writeTo(output, ExchangeStrategies.withDefaults()).then(Mono.defer(output::getBodyAsString))
                            .flatMap(body -> {
                                try {
                                    bodies.add(MAPPER.readTree(body));
                                } catch (Exception error) {
                                    return Mono.error(error);
                                }
                                return replies.apply(bodies.size() - 1);
                            });
                }).build();
        var registry = new SimpleMeterRegistry();
        var limiter = new AiRequestConcurrencyLimiter(1, registry);
        var client = new AiQuestionClientImpl(MAPPER, "stub", 800, webClient,
                new RepitMetrics(registry), limiter, settings);
        return new Harness(client, bodies, registry);
    }

    record Harness(AiQuestionClientImpl client, List<JsonNode> bodies, SimpleMeterRegistry registry) {}
}
