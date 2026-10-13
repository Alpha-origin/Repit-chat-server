package com.person.repit.tts.config;

import io.netty.channel.ChannelOption;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration
public class ElevenLabsWebClientConfig {

    @Bean("elevenLabsWebClient")
    public WebClient elevenLabsWebClient(TtsProperties properties) {
        TtsProperties.ElevenLabs config = properties.getElevenlabs();
        ConnectionProvider provider = ConnectionProvider.builder("elevenlabs")
                .maxConnections(config.getPool().getMaxConnections())
                .pendingAcquireMaxCount(config.getPool().getPendingAcquireMaxCount())
                .build();
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(config.getConnectTimeout().toMillis()))
                .responseTimeout(config.getResponseTimeout());

        return WebClient.builder()
                .baseUrl(config.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, "audio/mpeg")
                .build();
    }
}
