package com.person.repit.interview.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.interview.domain.InterviewResultDelivery;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.type.InterviewStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class InterviewResultOutbox {
    private static final String PREFIX = "chat:interview:result:";
    private final ReactiveStringRedisTemplate redis;
    private final ObjectMapper mapper;

    public Mono<InterviewResultDelivery> find(String sessionId) {
        return redis.opsForValue().get(PREFIX + sessionId)
                .flatMap(json -> Mono.fromCallable(() -> mapper.readValue(json, InterviewResultDelivery.class)));
    }

    public Mono<InterviewResultDelivery> enqueue(ChatInterviewAllRequest request) {
        return Mono.defer(() -> {
            if (request.getStatus() != InterviewStatus.COMPLETED
                    && request.getStatus() != InterviewStatus.ABANDONED) {
                return Mono.error(new IllegalArgumentException("명시적 종료 상태가 필요합니다."));
            }
            InterviewResultDelivery delivery = new InterviewResultDelivery();
            delivery.setSessionId(request.getSessionId());
            delivery.setInterviewId(request.getInterviewId());
            delivery.setInterviewStatus(request.getStatus());
            delivery.setNextAttemptAt(Instant.now());
            return Mono.fromCallable(() -> mapper.writeValueAsString(request))
                    .doOnNext(delivery::setPayload)
                    .then(Mono.fromCallable(() -> mapper.writeValueAsString(delivery)))
                    .flatMap(json -> redis.opsForValue().setIfAbsent(PREFIX + request.getSessionId(), json))
                    .flatMap(created -> Boolean.TRUE.equals(created)
                            ? Mono.just(delivery) : find(request.getSessionId()))
                    .switchIfEmpty(Mono.error(new IllegalStateException("면접 전송 대기 등록 실패")));
        });
    }

    public Mono<Void> save(InterviewResultDelivery delivery) {
        return Mono.fromCallable(() -> mapper.writeValueAsString(delivery))
                .flatMap(json -> redis.opsForValue().set(PREFIX + delivery.getSessionId(), json))
                .filter(Boolean.TRUE::equals)
                .switchIfEmpty(Mono.error(new IllegalStateException("면접 전송 상태 저장 실패")))
                .then();
    }

    public Flux<InterviewResultDelivery> findPending() {
        return redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(100).build())
                .concatMap(key -> find(key.substring(PREFIX.length())))
                .filter(item -> item.getState() == InterviewResultDelivery.State.PENDING);
    }
}
