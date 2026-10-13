package com.person.repit.tts.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.StoredAudio;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import software.amazon.awssdk.core.async.AsyncRequestBody;
import software.amazon.awssdk.services.s3.S3AsyncClient;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class S3AudioStorage implements AudioStorage {
    private final S3AsyncClient s3Client;
    private final TtsProperties properties;
    private final RepitMetrics metrics;

    @Override
    public Mono<StoredAudio> put(
            String sessionId,
            Long questionId,
            String inputHash,
            byte[] audio
    ) {
        TtsProperties.Storage storage = properties.getStorage();
        String random = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        String objectKey = "%s/%s/%d/%s-%s.mp3".formatted(
                trimSlashes(storage.getKeyPrefix()), sessionId, questionId,
                inputHash.substring(0, 8), random
        );
        PutObjectRequest request = PutObjectRequest.builder()
                .bucket(storage.getBucket())
                .key(objectKey)
                .contentType("audio/mpeg")
                .cacheControl(storage.getCacheControl())
                .build();

        Mono<StoredAudio> upload = Mono.fromFuture(() -> s3Client.putObject(
                        request, AsyncRequestBody.fromBytes(audio)
                ))
                .map(ignored -> new StoredAudio(objectKey, createUrl(storage, objectKey)))
                .doOnError(ignored -> metrics.ttsStorageFailure());
        return metrics.recordTtsStorage(upload);
    }

    private String createUrl(TtsProperties.Storage storage, String objectKey) {
        String baseUrl = StringUtils.hasText(storage.getPublicBaseUrl())
                ? storage.getPublicBaseUrl()
                : "https://%s.s3.%s.amazonaws.com".formatted(
                        storage.getBucket(), storage.getRegion()
                );
        return baseUrl.replaceAll("/+$", "") + "/" + objectKey;
    }

    private String trimSlashes(String value) {
        return value.replaceAll("^/+|/+$", "");
    }
}
