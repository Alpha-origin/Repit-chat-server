package com.person.repit.interview.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class ApiServerTransportTest {
    @Test
    void 실제_HTTP요청에_인증헤더와_확정_JSON본문을_전송하고_204도_성공처리한다() throws Exception {
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> body = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/interviews/result", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Internal-Token"));
            body.set(new String(exchange.getRequestBody().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        server.start();
        try {
            var client = new ApiServerClient("http://127.0.0.1:" + server.getAddress().getPort(),
                    "  test-internal-token  ", WebClient.builder(), new RepitMetrics(new SimpleMeterRegistry()));
            String json = "{\"questionId\":17,\"answer\":\"마지막 답변\"}";
            client.saveInterviewPayload(json).block(Duration.ofSeconds(5));
            assertThat(token.get()).isEqualTo("test-internal-token");
            assertThat(body.get()).isEqualTo(json);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void 응답이_없으면_10초에_타임아웃한다() {
        StepVerifier.withVirtualTime(() -> new ApiServerClient("http://localhost", "test",
                        WebClient.builder().exchangeFunction(request -> reactor.core.publisher.Mono.never()),
                        new RepitMetrics(new SimpleMeterRegistry())).saveInterviewPayload("{}"))
                .expectSubscription().expectNoEvent(Duration.ofSeconds(9))
                .thenAwait(Duration.ofSeconds(1)).expectError(TimeoutException.class).verify();
    }
}
