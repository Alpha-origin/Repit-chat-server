package com.person.repit.common.config;

import io.netty.channel.ChannelOption;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ReactorResourceFactory;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.time.Duration;

@Configuration(proxyBeanMethods = false)
public class AnthropicWebClientConfig {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    @Bean(name = "anthropicConnectionProvider", destroyMethod = "dispose")
    ConnectionProvider anthropicConnectionProvider(
            @Value("${anthropic.pool.max-connections:50}") int maxConnections,
            @Value("${anthropic.pool.pending-acquire-max-count:50}") int pendingAcquireMaxCount,
            @Value("${anthropic.pool.pending-acquire-timeout:2s}") Duration pendingAcquireTimeout
    ) {
        return ConnectionProvider.builder("anthropic")
                .maxConnections(maxConnections)
                .pendingAcquireMaxCount(pendingAcquireMaxCount)
                .pendingAcquireTimeout(pendingAcquireTimeout)
                .metrics(true)
                .build();
    }

    @Bean(name = "anthropicWebClient")
    WebClient anthropicWebClient(
            WebClient.Builder webClientBuilder,
            ReactorResourceFactory resourceFactory,
            @Qualifier("anthropicConnectionProvider") ConnectionProvider connectionProvider,
            @Value("${anthropic.base-url:https://api.anthropic.com}") String baseUrl,
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.connect-timeout:2s}") Duration connectTimeout,
            @Value("${anthropic.response-timeout:5s}") Duration responseTimeout
    ) {
        HttpClient httpClient = HttpClient.create(connectionProvider)
                .runOn(resourceFactory.getLoopResources())
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, Math.toIntExact(connectTimeout.toMillis()))
                .responseTimeout(responseTimeout)
                .metrics(true, ignored -> "/v1/messages");

        return webClientBuilder.clone()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
    }
}
