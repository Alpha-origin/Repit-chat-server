package com.person.repit.tts.config;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.http.HttpStatus;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.resources.ConnectionProvider;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElevenLabsWebClientConfigTest {

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 2147483648L})
    void TTS가_켜져_있으면_잘못된_수신_한도로_기동하지_않는다(long bytes) {
        var properties = properties(true, bytes);
        try (var context = context(properties, WebClient.builder())) {
            assertThatThrownBy(context::refresh).hasRootCauseInstanceOf(IllegalArgumentException.class);
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, 2147483648L})
    void TTS가_꺼져_있으면_잘못된_수신_한도로_서버_기동을_막지_않는다(long bytes) {
        var properties = properties(false, bytes);
        try (var context = context(properties, WebClient.builder())) {
            context.refresh();
            assertThat(context.getBean("elevenLabsWebClient")).isInstanceOf(WebClient.class);
        }
    }

    @Test
    void TTS_클라이언트는_주입된_빌더의_설정을_유지하고_원본을_바꾸지_않는다() {
        List<ClientRequest> requests = new ArrayList<>();
        var builder = WebClient.builder().baseUrl("http://original.test")
                .defaultHeader("X-Customizer", "retained")
                .exchangeFunction(request -> {
                    requests.add(request);
                    return Mono.just(ClientResponse.create(HttpStatus.OK).body("ok").build());
                });
        var properties = properties(true, 1024);
        properties.getElevenlabs().setBaseUrl("http://tts.test");

        try (var context = context(properties, builder)) {
            context.refresh();
            var client = context.getBean("elevenLabsWebClient", WebClient.class);
            client.get().uri("/probe").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
            builder.build().get().uri("/probe").retrieve().bodyToMono(String.class).block(Duration.ofSeconds(5));
        }

        assertThat(requests).hasSize(2);
        assertThat(requests.getFirst().headers().getFirst("X-Customizer")).isEqualTo("retained");
        assertThat(requests.getFirst().url().getHost()).isEqualTo("tts.test");
        assertThat(requests.getLast().url().getHost()).isEqualTo("original.test");
        assertThat(requests.getLast().headers().getAccept()).isEmpty();
    }

    @Test
    void 컨텍스트가_닫히면_실제_HTTP_연결을_사용한_풀을_정리한다() throws Exception {
        var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/probe", exchange -> {
            try (exchange) {
                exchange.sendResponseHeaders(204, -1);
            }
        });
        server.start();
        try {
            var properties = properties(true, 1024);
            properties.getElevenlabs().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
            ConnectionProvider provider;
            try (var context = context(properties, WebClient.builder())) {
                context.refresh();
                provider = context.getBean("elevenLabsConnectionProvider", ConnectionProvider.class);
                context.getBean("elevenLabsWebClient", WebClient.class).get().uri("/probe")
                        .retrieve().toBodilessEntity().block(Duration.ofSeconds(5));
            }
            assertThat(provider.isDisposed()).isTrue();
        } finally {
            server.stop(0);
        }
    }

    private TtsProperties properties(boolean enabled, long bytes) {
        var properties = new TtsProperties();
        properties.setEnabled(enabled);
        properties.getElevenlabs().setMaxInMemorySize(DataSize.ofBytes(bytes));
        return properties;
    }

    private AnnotationConfigApplicationContext context(TtsProperties properties, WebClient.Builder builder) {
        var context = new AnnotationConfigApplicationContext();
        context.registerBean(TtsProperties.class, () -> properties);
        context.registerBean(WebClient.Builder.class, () -> builder);
        context.register(ElevenLabsWebClientConfig.class);
        return context;
    }
}
