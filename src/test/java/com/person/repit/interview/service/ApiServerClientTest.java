package com.person.repit.interview.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.type.InterviewStatus;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@ExtendWith(OutputCaptureExtension.class)
class ApiServerClientTest {

    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    @Test
    void 면접_결과_요청에_공백을_제거한_내부_토큰을_추가한다() {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        ApiServerClient client = client("  internal-token  ", capturedRequest);

        client.saveInterviewResult(resultRequest()).block();

        assertThat(capturedRequest.get().headers().getFirst(INTERNAL_TOKEN_HEADER))
                .isEqualTo("internal-token");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void 내부_토큰이_비어_있으면_면접_결과_요청에_헤더를_추가하지_않는다(String token) {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        ApiServerClient client = client(token, capturedRequest);

        client.saveInterviewResult(resultRequest()).block();

        assertThat(capturedRequest.get().headers().getFirst(INTERNAL_TOKEN_HEADER)).isNull();
    }

    @ParameterizedTest
    @ValueSource(ints = {401, 403})
    void 내부_인증_실패는_상태를_로그로_남기고_예외를_전달한다(
            int statusCode,
            CapturedOutput output
    ) {
        AtomicReference<ClientRequest> capturedRequest = new AtomicReference<>();
        ApiServerClient client = client(
                "internal-token",
                capturedRequest,
                HttpStatus.valueOf(statusCode)
        );

        assertThatThrownBy(() -> client.saveInterviewResult(resultRequest()).block())
                .isInstanceOf(WebClientResponseException.class);
        assertThat(output)
                .contains("[면접 결과 저장 실패]")
                .contains("HTTP 상태=" + statusCode);
    }

    private ApiServerClient client(
            String internalToken,
            AtomicReference<ClientRequest> capturedRequest
    ) {
        return client(internalToken, capturedRequest, HttpStatus.OK);
    }

    private ApiServerClient client(
            String internalToken,
            AtomicReference<ClientRequest> capturedRequest,
            HttpStatus responseStatus
    ) {
        WebClient.Builder webClientBuilder = WebClient.builder()
                .exchangeFunction(request -> {
                    capturedRequest.set(request);

                    ClientResponse.Builder response = ClientResponse.create(responseStatus);
                    if (request.url().getPath().equals("/api/v1/ai")) {
                        response.header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                                .body("{}");
                    }
                    return Mono.just(response.build());
                });

        return new ApiServerClient(
                "http://localhost",
                internalToken,
                webClientBuilder,
                new RepitMetrics(new SimpleMeterRegistry())
        );
    }

    private ChatInterviewAllRequest resultRequest() {
        return ChatInterviewAllRequest.builder()
                .sessionId("session-id")
                .interviewId(1L)
                .userId(2L)
                .status(InterviewStatus.COMPLETED)
                .qnaRequests(List.of())
                .build();
    }
}
