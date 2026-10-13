package com.person.repit.tts.service;

import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.domain.AudioStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Repository
@RequiredArgsConstructor
public class AudioStateRepository {
    private static final String KEY_PREFIX = "chat:interview:audio:";
    private static final Duration TTL = Duration.ofHours(3).plusMinutes(30);

    private final ReactiveRedisTemplate<String, Object> redisTemplate;

    public Mono<AudioState> get(String sessionId, Long questionId) {
        return redisTemplate.opsForValue().get(key(sessionId, questionId))
                .ofType(AudioState.class);
    }

    public Mono<Boolean> create(AudioState state) {
        return redisTemplate.opsForValue().setIfAbsent(
                key(state.getSessionId(), state.getQuestionId()), state, TTL
        );
    }

    public Mono<Void> save(AudioState state) {
        return redisTemplate.opsForValue()
                .set(key(state.getSessionId(), state.getQuestionId()), state, TTL)
                .then();
    }

    public Mono<Void> delete(String sessionId, Long questionId) {
        return redisTemplate.delete(key(sessionId, questionId)).then();
    }

    public Flux<AudioState> findRecoverable() {
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(100).build();
        return redisTemplate.scan(options)
                .flatMap(redisTemplate.opsForValue()::get)
                .ofType(AudioState.class)
                .filter(state -> state.getStatus() == AudioStatus.QUEUED
                        || state.getStatus() == AudioStatus.GENERATING);
    }

    private String key(String sessionId, Long questionId) {
        return KEY_PREFIX + sessionId + ":" + questionId;
    }
}
