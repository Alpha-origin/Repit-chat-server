package com.person.repit.tts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.nio.netty.NettyNioAsyncHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3AsyncClient;

@Configuration
public class S3Config {

    @Bean
    public S3AsyncClient s3AsyncClient(TtsProperties properties) {
        TtsProperties.Storage storage = properties.getStorage();
        return S3AsyncClient.builder()
                .region(Region.of(storage.getRegion()))
                .credentialsProvider(credentialsProvider(storage))
                .httpClientBuilder(NettyNioAsyncHttpClient.builder()
                        .maxConcurrency(properties.getElevenlabs().getPool().getMaxConnections())
                        .connectionTimeout(properties.getElevenlabs().getConnectTimeout()))
                .build();
    }

    private AwsCredentialsProvider credentialsProvider(TtsProperties.Storage storage) {
        if (StringUtils.hasText(storage.getAccessKey()) && StringUtils.hasText(storage.getSecretKey())) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(storage.getAccessKey(), storage.getSecretKey())
            );
        }
        return DefaultCredentialsProvider.create();
    }
}
