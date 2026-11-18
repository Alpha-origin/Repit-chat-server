package com.person.repit.common.ratelimit;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Component
@RequiredArgsConstructor
public class RequestRateLimiter {

    private final ReactiveStringRedisTemplate redis;

    private static final DefaultRedisScript<Long> COUNT =
            new DefaultRedisScript<>("""
                    local count = redis.call('INCR', KEYS[1])
                    if count == 1 then
                        redis.call('PEXPIRE', KEYS[1], ARGV[1])
                    end
                    return count
                    """, Long.class);

    public Mono<Boolean> allow(
            String bucket,
            int limit,
            Duration window
    ) {

        if (bucket == null || bucket.isBlank()
                || limit <= 0
                || window == null
                || window.toMillis() <= 0) {
            return Mono.error(new IllegalArgumentException(
                    "bucket, limit, window 설정이 올바르지 않습니다."
            ));
        }

        return redis.execute(
                        COUNT,
                        List.of("repit:rate:" + bucket),
                        List.of(Long.toString(window.toMillis()))
                )
                .single()
                .map(count -> count <= limit);
    }

}
