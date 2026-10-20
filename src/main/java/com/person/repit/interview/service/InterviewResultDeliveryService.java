package com.person.repit.interview.service;

import com.person.repit.interview.domain.InterviewResultDelivery;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.exception.ApiServerResponseException;
import com.person.repit.interview.type.InterviewStatus;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewResultDeliveryService {
    private final InterviewResultOutbox outbox;
    private final ApiServerClient client;
    private final InterviewResultRetryPolicy policy;
    private final MeterRegistry meters;

    public Mono<ChatProgressResponse> resume(String sessionId) {
        return outbox.find(sessionId).flatMap(this::requireTerminalStatus).flatMap(item ->
                item.getState() == InterviewResultDelivery.State.BLOCKED
                        ? Mono.error(new ApiServerResponseException("면접 기록 전송 설정 확인이 필요합니다."))
                        : Mono.just(response(item)));
    }

    public Mono<ChatProgressResponse> submit(ChatInterviewAllRequest request) {
        return outbox.enqueue(request).flatMap(this::deliver).map(this::response);
    }

    // Call under InterviewOperationLock, shared by requests and recovery workers.
    public Mono<InterviewResultDelivery> deliver(InterviewResultDelivery delivery) {
        return Mono.defer(() -> requireTerminalStatus(delivery).flatMap(this::deliverValidated));
    }

    private Mono<InterviewResultDelivery> requireTerminalStatus(InterviewResultDelivery delivery) {
        if (delivery.getInterviewStatus() == InterviewStatus.COMPLETED
                || delivery.getInterviewStatus() == InterviewStatus.ABANDONED) {
            return Mono.just(delivery);
        }
        var error = new ApiServerResponseException("면접 전송 기록의 명시적 종료 상태 확인이 필요합니다.");
        if (delivery.getState() == InterviewResultDelivery.State.BLOCKED
                || delivery.getState() == InterviewResultDelivery.State.SENT) {
            return Mono.error(error);
        }
        // Preserve the original payload; never infer completion from legacy records or answers.
        return recordFailure(delivery, error).then(Mono.error(error));
    }

    private Mono<InterviewResultDelivery> deliverValidated(InterviewResultDelivery delivery) {
        return Mono.defer(() -> {
            if (delivery.getState() == InterviewResultDelivery.State.BLOCKED) {
                return Mono.error(new ApiServerResponseException("면접 기록 전송 설정 확인이 필요합니다."));
            }
            if (delivery.getState() == InterviewResultDelivery.State.SENT
                    || delivery.getNextAttemptAt().isAfter(Instant.now())) {
                return Mono.just(delivery);
            }
            return client.saveInterviewPayload(delivery.getPayload())
                    .thenReturn(true)
                    .onErrorResume(error -> recordFailure(delivery, error).thenReturn(false))
                    .flatMap(sent -> {
                        if (!sent) {
                            return Mono.just(delivery);
                        }
                        delivery.setState(InterviewResultDelivery.State.SENT);
                        delivery.setPayload(null);
                        return outbox.save(delivery).thenReturn(delivery);
                    });
        });
    }

    private Mono<Void> recordFailure(InterviewResultDelivery delivery, Throwable error) {
        Instant now = Instant.now();
        delivery.setAttempts(delivery.getAttempts() + 1);
        if (delivery.getFirstFailureAt() == null) {
            delivery.setFirstFailureAt(now);
        }
        delivery.setLastError(policy.errorType(error));
        boolean retryable = policy.isRetryable(error);
        delivery.setNextAttemptAt(policy.nextAttempt(delivery.getAttempts(), error, now));
        if (!retryable) {
            delivery.setState(InterviewResultDelivery.State.BLOCKED);
        }
        if (!retryable || policy.needsAttention(delivery.getAttempts(), delivery.getFirstFailureAt(), now)) {
            meters.counter("repit.interview.result.attention", "error", delivery.getLastError()).increment();
            log.error("면접 기록 전송 운영 확인 필요 sessionId={}, attempts={}, type={}",
                    delivery.getSessionId(), delivery.getAttempts(), delivery.getLastError());
        }
        return outbox.save(delivery).then(retryable ? Mono.empty() : Mono.error(error));
    }

    private ChatProgressResponse response(InterviewResultDelivery delivery) {
        return delivery.getInterviewStatus() == InterviewStatus.ABANDONED
                ? ChatProgressResponse.quit() : ChatProgressResponse.end();
    }
}
