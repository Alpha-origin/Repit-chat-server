package com.person.repit.interview.service;

import com.person.repit.common.exception.AlreadyUsingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Slf4j
@Component
@RequiredArgsConstructor
public class InterviewOperationLock {
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration OPERATION_TIMEOUT = Duration.ofSeconds(45);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then "
                    + "return redis.call('del', KEYS[1]) else return 0 end", Long.class);
    private final ReactiveStringRedisTemplate redis;

    public <T> Mono<T> execute(String sessionId, Supplier<Mono<T>> operation) {
        return Mono.defer(() -> {
            String key = "chat:interview:operation-lock:" + sessionId;
            String token = UUID.randomUUID().toString();
            Mono<String> acquire = redis.opsForValue().setIfAbsent(key, token, LEASE)
                    .filter(Boolean.TRUE::equals)
                    .switchIfEmpty(Mono.error(new AlreadyUsingException("면접 요청 처리 중입니다.")))
                    .thenReturn(token);
            return Mono.usingWhen(acquire,
                    ignored -> Mono.defer(operation).timeout(OPERATION_TIMEOUT),
                    ignored -> release(key, token),
                    (ignored, error) -> release(key, token),
                    ignored -> release(key, token));
        });
    }

    private Mono<Void> release(String key, String token) {
        return redis.execute(RELEASE, List.of(key), List.of(token)).then()
                .onErrorResume(error -> {
                    log.warn("면접 작업 잠금 해제 실패 type={}", error.getClass().getSimpleName());
                    return Mono.empty();
                });
    }
}
