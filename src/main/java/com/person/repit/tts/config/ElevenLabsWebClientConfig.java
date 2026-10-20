package com.person.repit.tts.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.util.Assert;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Configuration(proxyBeanMethods = false)
public class ElevenLabsWebClientConfig {

    @Bean(name = "elevenLabsConnectionProvider", destroyMethod = "dispose")
    public ConnectionProvider elevenLabsConnectionProvider(TtsProperties properties) {
        TtsProperties.Pool pool = properties.getElevenlabs().getPool();
        return ConnectionProvider.builder("elevenlabs")
                .maxConnections(pool.getMaxConnections())
                .pendingAcquireMaxCount(pool.getPendingAcquireMaxCount())
                .build();
    }

    @Bean("elevenLabsWebClient")
    public WebClient elevenLabsWebClient(
            WebClient.Builder webClientBuilder,
            @Qualifier("elevenLabsConnectionProvider") ConnectionProvider provider,
            TtsProperties properties
    ) {
        TtsProperties.ElevenLabs config = properties.getElevenlabs();
        var size = properties.isEnabled()
                ? config.getMaxInMemorySize() : TtsProperties.ElevenLabs.DEFAULT_MAX_IN_MEMORY_SIZE;
        Assert.notNull(size, "tts.elevenlabs.max-in-memory-size는 필수입니다");
        long bytes = size.toBytes();
        Assert.isTrue(bytes > 0 && bytes <= Integer.MAX_VALUE,
                "tts.elevenlabs.max-in-memory-size는 1~2147483647바이트여야 합니다");
        int maxInMemorySize = Math.toIntExact(bytes);
        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS,
                        Math.toIntExact(config.getConnectTimeout().toMillis()))
                .responseTimeout(config.getResponseTimeout());

        return webClientBuilder.clone()
                .baseUrl(config.getBaseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxInMemorySize))
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.ACCEPT, "audio/mpeg")
                .build();
    }
}
