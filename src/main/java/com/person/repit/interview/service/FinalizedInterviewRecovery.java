package com.person.repit.interview.service;

import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.InterviewResultDelivery;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.tts.service.InterviewTtsScheduler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

@Slf4j
@Component
@RequiredArgsConstructor
public class FinalizedInterviewRecovery {
    private static final String PREFIX = "chat:interview:";
    private final ReactiveRedisTemplate<String, Object> redis;
    private final InterviewResultOutbox outbox;
    private final InterviewOperationLock lock;
    private final InterviewTtsScheduler tts;

    // Recover a crash between saving the final session and registering its delivery.
    public Mono<Void> recover() {
        return redis.scan(ScanOptions.scanOptions().match(PREFIX + "*").count(100).build())
                .filter(key -> !key.startsWith(PREFIX + "audio:")
                        && !key.startsWith(PREFIX + "result:")
                        && !key.startsWith(PREFIX + "operation-lock:"))
                .concatMap(key -> redis.opsForValue().get(key).ofType(ChatInterviewSession.class))
                .filter(session -> session.getStatus() == InterviewStatus.COMPLETED
                        || session.getStatus() == InterviewStatus.ABANDONED)
                .concatMap(session -> lock.execute(session.getSessionId(), () ->
                                redis.opsForValue().get(PREFIX + session.getSessionId())
                                        .ofType(ChatInterviewSession.class)
                                        .filter(current -> current.getStatus() == InterviewStatus.COMPLETED
                                                || current.getStatus() == InterviewStatus.ABANDONED)
                                        .doOnNext(current -> tts.cancelSession(current.getSessionId()))
                                        .flatMap(current -> outbox.find(current.getSessionId())
                                                .switchIfEmpty(Mono.defer(() -> outbox.enqueue(
                                                        ChatInterviewAllRequest.from(current)))))
                                        .filter(item -> item.getState() != InterviewResultDelivery.State.BLOCKED)
                                        .flatMap(item -> redis.delete(PREFIX + item.getSessionId())))
                        .onErrorResume(error -> {
                            log.warn("종료 면접 복구 대기 sessionId={}, type={}",
                                    session.getSessionId(), error.getClass().getSimpleName());
                            return Mono.empty();
                        }))
                .then();
    }
}
