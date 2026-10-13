package com.person.repit.tts.service;

import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.TtsInput;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

@Component
public class ElevenLabsTtsClient implements TtsClient {
    private final WebClient webClient;
    private final TtsProperties properties;

    public ElevenLabsTtsClient(
            @Qualifier("elevenLabsWebClient") WebClient webClient,
            TtsProperties properties
    ) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public Mono<byte[]> synthesize(TtsInput input) {
        if (input.voiceId() == null || input.voiceId().isBlank()) {
            return Mono.error(new TtsClientException("TTS 음성이 설정되지 않았습니다", false));
        }
        Map<String, Object> body = Map.of(
                "text", input.text(),
                "model_id", input.modelId(),
                "voice_settings", input.voiceSettings()
        );
        return webClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/v1/text-to-speech/{voiceId}")
                        .queryParam("output_format", input.outputFormat())
                        .build(input.voiceId()))
                .header("xi-api-key", properties.getElevenlabs().getApiKey())
                .bodyValue(body)
                .exchangeToMono(this::readAudio);
    }

    private Mono<byte[]> readAudio(ClientResponse response) {
        int status = response.statusCode().value();
        if (status != 200) {
            boolean retryable = status == 429 || status >= 500;
            return response.releaseBody().then(Mono.error(
                    new TtsClientException("ElevenLabs 응답 오류: " + status, retryable)
            ));
        }
        MediaType contentType = response.headers().contentType().orElse(null);
        if (contentType == null || !"audio".equalsIgnoreCase(contentType.getType())) {
            return response.releaseBody().then(Mono.error(
                    new TtsClientException("ElevenLabs 응답이 오디오가 아닙니다", false)
            ));
        }
        return response.bodyToMono(byte[].class)
                .filter(bytes -> bytes.length > 0)
                .switchIfEmpty(Mono.error(
                        new TtsClientException("ElevenLabs 오디오가 비어 있습니다", false)
                ));
    }
}
