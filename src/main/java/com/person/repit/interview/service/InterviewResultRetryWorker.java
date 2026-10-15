package com.person.repit.interview.service;

import com.person.repit.common.exception.AlreadyUsingException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Component
@EnableScheduling
@RequiredArgsConstructor
public class InterviewResultRetryWorker {
    private final InterviewResultOutbox outbox;
    private final InterviewResultDeliveryService delivery;
    private final InterviewOperationLock lock;
    private final FinalizedInterviewRecovery recovery;
    @Value("${repit.interview-result.retry-enabled:false}")
    private boolean enabled;

    @Scheduled(fixedDelayString = "${repit.interview-result.poll-interval:5000}")
    public Mono<Void> retryPending() {
        return Mono.defer(() -> {
            if (!enabled) {
                return Mono.empty();
            }
            return recovery.recover().thenMany(outbox.findPending())
                    .filter(item -> !item.getNextAttemptAt().isAfter(Instant.now()))
                    .flatMap(item -> lock.execute(item.getSessionId(), () ->
                                    outbox.find(item.getSessionId()).flatMap(delivery::deliver))
                            .onErrorResume(error -> {
                                if (!(error instanceof AlreadyUsingException)) {
                                    log.warn("면접 기록 재전송 실패 sessionId={}, type={}",
                                            item.getSessionId(), error.getClass().getSimpleName());
                                }
                                return Mono.empty();
                            }), 4)
                    .then()
                    .onErrorResume(error -> {
                        log.warn("면접 기록 대기열 조회 실패 type={}", error.getClass().getSimpleName());
                        return Mono.empty();
                    });
        });
    }
}
