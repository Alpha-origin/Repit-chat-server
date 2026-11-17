package com.person.repit.common.ratelimit;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.PathContainer;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import org.springframework.web.util.pattern.PathPattern;
import org.springframework.web.util.pattern.PathPatternParser;
import reactor.core.publisher.Mono;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestRateLimitFilter implements WebFilter {

    private static final PathPattern PREPARE = path("/chat/interviews");
    private static final PathPattern SESSION = path("/chat/interviews/{sessionId}");
    private static final PathPattern QUESTION = path("/chat/interviews/{sessionId}/question");
    private static final PathPattern ANSWERS = path("/chat/interviews/{sessionId}/answers");
    private static final PathPattern COMPLETE = path("/chat/interviews/{sessionId}/complete");
    private static final PathPattern QUIT = path("/chat/interviews/{sessionId}/quit");
    private static final PathPattern AUDIO = path("/chat/interviews/{sessionId}/questions/{questionId}/audio");
    private static final PathPattern WEBSOCKET = path("/ws/chat/interviews");

    private static final Policy PREPARE_POLICY = new Policy("prepare", 10, Duration.ofMinutes(1));
    private static final Policy READ_POLICY = new Policy("read", 30, Duration.ofSeconds(10));
    private static final Policy WRITE_POLICY = new Policy("write", 10, Duration.ofSeconds(10));
    private static final Policy AUDIO_POLICY = new Policy("audio", 30, Duration.ofSeconds(10));
    private static final Policy CONNECT_POLICY = new Policy("ws-connect", 10, Duration.ofSeconds(10));

    private final RequestRateLimiter rateLimiter;

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        Policy policy = policyFor(exchange);
        if (policy == null) {
            return chain.filter(exchange);
        }

        return caller(exchange)
                .flatMap(caller -> rateLimiter.allow(
                                "http:" + policy.name() + ":" + caller,
                                policy.limit(),
                                policy.window()
                        )
                        .map(allowed -> allowed ? Decision.ALLOW : Decision.LIMIT)
                        .onErrorResume(exception -> {
                            log.warn("[RATE LIMIT CHECK FAILED] policy={}", policy.name(), exception);
                            return Mono.just(Decision.UNAVAILABLE);
                        }))
                .flatMap(decision -> switch (decision) {
                    case ALLOW -> chain.filter(exchange);
                    case LIMIT -> respond(exchange, HttpStatus.TOO_MANY_REQUESTS,
                            "RATE_LIMITED", "잠시 후 다시 요청해주세요.", policy.window().toSeconds());
                    case UNAVAILABLE -> respond(exchange, HttpStatus.SERVICE_UNAVAILABLE,
                            "RATE_LIMIT_UNAVAILABLE", "잠시 후 다시 요청해주세요.", 1);
                });
    }

    private Policy policyFor(ServerWebExchange exchange) {
        HttpMethod method = exchange.getRequest().getMethod();
        PathContainer path = exchange.getRequest().getPath().pathWithinApplication();

        if (HttpMethod.POST.equals(method)) {
            if (PREPARE.matches(path)) {
                return PREPARE_POLICY;
            }
            if (ANSWERS.matches(path) || COMPLETE.matches(path) || QUIT.matches(path)) {
                return WRITE_POLICY;
            }
        }
        if (HttpMethod.GET.equals(method)) {
            if (AUDIO.matches(path)) {
                return AUDIO_POLICY;
            }
            if (SESSION.matches(path) || QUESTION.matches(path)) {
                return READ_POLICY;
            }
            if (WEBSOCKET.matches(path)) {
                return CONNECT_POLICY;
            }
        }
        return null;
    }

    private Mono<String> caller(ServerWebExchange exchange) {
        return exchange.<Principal>getPrincipal()
                .flatMap(principal -> Mono.justOrEmpty(principal.getName()))
                .filter(name -> !name.isBlank())
                .map(name -> "user:" + name)
                .switchIfEmpty(Mono.fromSupplier(() -> {
                    // Forwarded headers require a separately configured, trusted proxy boundary.
                    InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
                    String address = remote == null || remote.getAddress() == null
                            ? "unknown" : remote.getAddress().getHostAddress();
                    return "ip:" + address;
                }));
    }

    private Mono<Void> respond(ServerWebExchange exchange, HttpStatus status,
                               String code, String message, long retryAfter) {
        var response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        response.getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
        response.getHeaders().setCacheControl("no-store");
        byte[] body = ("{\"status\":" + status.value() + ",\"code\":\"" + code
                + "\",\"message\":\"" + message + "\"}").getBytes(StandardCharsets.UTF_8);
        return response.writeWith(Mono.just(response.bufferFactory().wrap(body)));
    }

    private static PathPattern path(String pattern) {
        return PathPatternParser.defaultInstance.parse(pattern);
    }

    private record Policy(String name, int limit, Duration window) { }

    private enum Decision { ALLOW, LIMIT, UNAVAILABLE }
}
