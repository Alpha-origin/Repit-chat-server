package com.person.repit.common.ratelimit;

import com.person.repit.common.config.CorsConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.config.EnableWebFlux;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.net.InetSocketAddress;
import java.security.Principal;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RequestRateLimitFilterTest {

    private RequestRateLimiter limiter;
    private RequestRateLimitFilter filter;
    private WebFilterChain chain;

    @BeforeEach
    void setUp() {
        limiter = mock(RequestRateLimiter.class);
        when(limiter.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(Mono.just(true));
        filter = new RequestRateLimitFilter(limiter);
        chain = mock(WebFilterChain.class);
        when(chain.filter(any())).thenReturn(Mono.empty());
    }

    @ParameterizedTest
    @CsvSource({
            "POST, /chat/interviews, prepare, 10, 60",
            "GET, /chat/interviews/s, read, 30, 10",
            "GET, /chat/interviews/s/question, read, 30, 10",
            "POST, /chat/interviews/s/answers, write, 10, 10",
            "POST, /chat/interviews/s/complete, write, 10, 10",
            "POST, /chat/interviews/s/quit, write, 10, 10",
            "GET, /chat/interviews/s/questions/1/audio?wait=20, audio, 30, 10",
            "GET, /ws/chat/interviews?sessionId=s, ws-connect, 10, 10"
    })
    void 대상_요청은_정책별로_접속_IP를_제한한다(
            String method, String path, String policy, int limit, long seconds) {
        var exchange = exchange(HttpMethod.valueOf(method), path);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(limiter).allow("http:" + policy + ":ip:198.51.100.7", limit, Duration.ofSeconds(seconds));
        verify(chain).filter(exchange);
    }

    @ParameterizedTest
    @CsvSource({
            "OPTIONS, /chat/interviews",
            "OPTIONS, /chat/interviews/s/answers",
            "GET, /actuator/health",
            "GET, /actuator/prometheus",
            "GET, /other",
            "GET, /chat/interviews/s/unknown",
            "POST, /chat/interviews/s/questions/1/audio"
    })
    void 사전_요청과_대상이_아닌_요청은_통과시킨다(String method, String path) {
        var exchange = exchange(HttpMethod.valueOf(method), path);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verifyNoInteractions(limiter);
        verify(chain).filter(exchange);
    }

    @Test
    void 초과한_요청은_서비스를_호출하지_않고_429와_재시도_시간을_반환한다() {
        when(limiter.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(Mono.just(false));
        var exchange = exchange(HttpMethod.POST, "/chat/interviews");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("60");
        assertThat(exchange.getResponse().getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("\"status\":429", "RATE_LIMITED");
        verify(chain, never()).filter(any());
    }

    @Test
    void 인증된_사용자는_접속_IP_대신_사용자_ID로_제한한다() {
        var exchange = exchange(HttpMethod.POST, "/chat/interviews").mutate()
                .principal(Mono.just((Principal) () -> "user-42")).build();

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(limiter).allow("http:prepare:user:user-42", 10, Duration.ofMinutes(1));
    }

    @Test
    void 클라이언트가_전달한_사용자와_IP_헤더로_제한을_우회할_수_없다() {
        var request = MockServerHttpRequest.post("/chat/interviews")
                .remoteAddress(new InetSocketAddress("198.51.100.7", 12345))
                .header("CF-Connecting-IP", "203.0.113.8")
                .header("X-Forwarded-For", "203.0.113.9")
                .header("X-User-Id", "fake-user")
                .body("{\"userId\":999}");
        var exchange = MockServerWebExchange.from(request);

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(limiter).allow("http:prepare:ip:198.51.100.7", 10, Duration.ofMinutes(1));
    }

    @Test
    void 세션_ID를_바꿔도_같은_호출자의_답변_제한을_공유한다() {
        StepVerifier.create(filter.filter(exchange(HttpMethod.POST, "/chat/interviews/a/answers"), chain))
                .verifyComplete();
        StepVerifier.create(filter.filter(exchange(HttpMethod.POST, "/chat/interviews/b/answers"), chain))
                .verifyComplete();

        verify(limiter, times(2)).allow("http:write:ip:198.51.100.7", 10, Duration.ofSeconds(10));
    }

    @Test
    void 접속_주소가_없어도_공통_제한을_적용한다() {
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/chat/interviews"));

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        verify(limiter).allow("http:prepare:ip:unknown", 10, Duration.ofMinutes(1));
    }

    @Test
    void Redis_장애는_요청을_실행하지_않고_503으로_반환한다() {
        when(limiter.allow(anyString(), anyInt(), any(Duration.class)))
                .thenReturn(Mono.error(new IllegalStateException("internal redis details")));
        var exchange = exchange(HttpMethod.POST, "/chat/interviews");

        StepVerifier.create(filter.filter(exchange, chain)).verifyComplete();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
        assertThat(exchange.getResponse().getHeaders().getFirst(HttpHeaders.RETRY_AFTER)).isEqualTo("1");
        assertThat(exchange.getResponse().getBodyAsString().block())
                .contains("RATE_LIMIT_UNAVAILABLE").doesNotContain("internal redis details");
        verify(chain, never()).filter(any());
    }

    @Test
    void 서비스_오류를_요청_제한_장애로_변환하지_않는다() {
        var failure = new IllegalStateException("service failure");
        when(chain.filter(any())).thenReturn(Mono.error(failure));

        StepVerifier.create(filter.filter(exchange(HttpMethod.POST, "/chat/interviews"), chain))
                .expectErrorMatches(error -> error == failure).verify();
    }

    @Test
    void 브라우저가_429_응답과_Retry_After_헤더를_읽을_수_있다() {
        try (var context = new AnnotationConfigApplicationContext(WebConfig.class)) {
            var webLimiter = context.getBean(RequestRateLimiter.class);
            when(webLimiter.allow(anyString(), anyInt(), any(Duration.class))).thenReturn(Mono.just(false));
            var client = WebTestClient.bindToApplicationContext(context).configureClient()
                    .baseUrl("http://localhost").build();

            client.post().uri("/chat/interviews")
                    .header(HttpHeaders.ORIGIN, "https://team-alpha.org")
                    .exchange()
                    .expectStatus().isEqualTo(429)
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://team-alpha.org")
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, "Retry-After")
                    .expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "60")
                    .expectBody().jsonPath("$.code").isEqualTo("RATE_LIMITED");
        }
    }

    @Test
    void CORS_사전_요청은_Redis를_호출하지_않는다() {
        try (var context = new AnnotationConfigApplicationContext(WebConfig.class)) {
            var client = WebTestClient.bindToApplicationContext(context).configureClient()
                    .baseUrl("http://localhost").build();

            client.options().uri("/chat/interviews")
                    .header(HttpHeaders.ORIGIN, "https://team-alpha.org")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
                    .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Content-Type")
                    .exchange()
                    .expectStatus().isOk()
                    .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://team-alpha.org");

            verifyNoInteractions(context.getBean(RequestRateLimiter.class));
        }
    }

    private MockServerWebExchange exchange(HttpMethod method, String path) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(method, path)
                .remoteAddress(new InetSocketAddress("198.51.100.7", 12345)).build());
    }

    @Configuration
    @EnableWebFlux
    @Import({CorsConfig.class, RequestRateLimitFilter.class})
    static class WebConfig {
        @Bean
        RequestRateLimiter requestRateLimiter() {
            return mock(RequestRateLimiter.class);
        }
    }
}
