package com.person.repit.tts.service;

import com.person.repit.tts.config.ElevenLabsWebClientConfig;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.TtsInput;
import com.person.repit.tts.domain.VoiceSettings;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.buffer.DataBufferLimitException;
import reactor.test.StepVerifier;
import reactor.netty.resources.ConnectionProvider;
import org.springframework.util.unit.DataSize;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ElevenLabsTtsClientTest {
    private HttpServer server;
    private TtsProperties properties;
    private ConnectionProvider provider;

    @BeforeEach
    void setUp() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.start();
        properties = new TtsProperties();
        properties.setEnabled(true);
        provider = new ElevenLabsWebClientConfig().elevenLabsConnectionProvider(properties);
        properties.getElevenlabs().setBaseUrl("http://127.0.0.1:" + server.getAddress().getPort());
        properties.getElevenlabs().setApiKey("test-key");
    }

    @AfterEach
    void tearDown() {
        provider.disposeLater().block(Duration.ofSeconds(5));
        server.stop(0);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 기본_256KiB보다_큰_오디오를_잘리지_않고_읽는다(boolean chunked) {
        byte[] audio = new byte[320 * 1024];
        Arrays.fill(audio, (byte) 42);
        respond(200, "audio/mpeg", audio, chunked);

        byte[] result = client().synthesize(input()).block(Duration.ofSeconds(5));

        assertThat(result).isEqualTo(audio);
    }

    @Test
    void 설정한_한도와_같은_크기의_오디오는_허용한다() {
        properties.getElevenlabs().setMaxInMemorySize(DataSize.ofKilobytes(64));
        byte[] audio = new byte[64 * 1024];
        respond(200, "audio/mpeg", audio, false);

        assertThat(client().synthesize(input()).block(Duration.ofSeconds(5))).isEqualTo(audio);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 설정한_한도를_넘으면_재시도하지_않는_오류로_반환한다(boolean chunked) {
        properties.getElevenlabs().setMaxInMemorySize(DataSize.ofKilobytes(64));
        respond(200, "audio/mpeg", new byte[64 * 1024 + 1], chunked);

        StepVerifier.create(client().synthesize(input()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(TtsClientException.class)
                            .hasCauseInstanceOf(DataBufferLimitException.class);
                    assertThat(((TtsClientException) error).isRetryable()).isFalse();
                    assertThat(((TtsClientException) error).getErrorCode()).isEqualTo("TTS_AUDIO_TOO_LARGE");
                }).verify(Duration.ofSeconds(5));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void 무제한이나_잘못된_수신_한도를_허용하지_않는다(int limit) {
        properties.getElevenlabs().setMaxInMemorySize(DataSize.ofBytes(limit));

        assertThatThrownBy(this::client).isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-in-memory-size");
    }

    @ParameterizedTest
    @CsvSource({"524288,524288", "512KB,524288", "8MB,8388608"})
    void 바이트와_단위가_있는_수신_한도를_설정에서_바인딩한다(String value, long bytes) {
        var binder = new Binder(new MapConfigurationPropertySource(Map.of(
                "tts.elevenlabs.max-in-memory-size", value)));

        var bound = binder.bind("tts", Bindable.of(TtsProperties.class)).get();

        assertThat(bound.getElevenlabs().getMaxInMemorySize()).isEqualTo(DataSize.ofBytes(bytes));
    }

    @ParameterizedTest
    @CsvSource({"400,false", "429,true", "503,true"})
    void HTTP_오류의_기존_재시도_분류를_유지한다(int status, boolean retryable) {
        respond(status, "application/json", new byte[]{1}, false);

        StepVerifier.create(client().synthesize(input()))
                .expectErrorSatisfies(error -> {
                    assertThat(error).isInstanceOf(TtsClientException.class);
                    assertThat(((TtsClientException) error).isRetryable()).isEqualTo(retryable);
                    assertThat(((TtsClientException) error).getErrorCode()).isEqualTo("TTS_GENERATION_FAILED");
                }).verify(Duration.ofSeconds(5));
    }

    @Test
    void 오디오가_아닌_응답을_거절한다() {
        respond(200, "application/json", new byte[]{1}, false);

        StepVerifier.create(client().synthesize(input()))
                .expectError(TtsClientException.class).verify(Duration.ofSeconds(5));
    }

    @Test
    void 빈_오디오는_거절한다() {
        respond(200, "audio/mpeg", new byte[0], true);

        StepVerifier.create(client().synthesize(input()))
                .expectError(TtsClientException.class).verify(Duration.ofSeconds(5));
    }

    private ElevenLabsTtsClient client() {
        return new ElevenLabsTtsClient(
                new ElevenLabsWebClientConfig().elevenLabsWebClient(WebClient.builder(), provider, properties), properties);
    }

    private TtsInput input() {
        return new TtsInput("질문", "voice", "model", "mp3_44100_128",
                new VoiceSettings(0.5, 0.8, 0.1, 1.0, true), "test-hash");
    }

    private void respond(int status, String contentType, byte[] body, boolean chunked) {
        server.createContext("/v1/text-to-speech/voice", exchange -> {
            try (exchange) {
                exchange.getRequestBody().readAllBytes();
                exchange.getResponseHeaders().set("Content-Type", contentType);
                exchange.sendResponseHeaders(status, chunked ? 0 : body.length);
                exchange.getResponseBody().write(body);
            }
        });
    }
}
