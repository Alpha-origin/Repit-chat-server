package com.person.repit.interview.service;

import com.person.repit.interview.domain.InterviewResultDelivery;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.function.Supplier;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InterviewResultRetryWorkerTest {
    private final InterviewResultOutbox outbox = mock(InterviewResultOutbox.class);
    private final InterviewResultDeliveryService delivery = mock(InterviewResultDeliveryService.class);
    private final InterviewOperationLock lock = mock(InterviewOperationLock.class);
    private final FinalizedInterviewRecovery recovery = mock(FinalizedInterviewRecovery.class);
    private final InterviewResultRetryWorker worker = new InterviewResultRetryWorker(outbox, delivery, lock, recovery);

    @Test
    void API_중복저장방지_배포전에는_자동재전송하지_않는다() {
        worker.retryPending().block();
        verifyNoInteractions(outbox, delivery, lock, recovery);
    }

    @Test
    @SuppressWarnings("unchecked")
    void 재시도시각이_지난_작업만_잠금후_최신상태로_전송한다() {
        ReflectionTestUtils.setField(worker, "enabled", true);
        var due = new InterviewResultDelivery();
        due.setSessionId("due");
        due.setNextAttemptAt(Instant.EPOCH);
        var later = new InterviewResultDelivery();
        later.setSessionId("later");
        later.setNextAttemptAt(Instant.now().plusSeconds(3600));
        when(recovery.recover()).thenReturn(Mono.empty());
        when(outbox.findPending()).thenReturn(Flux.just(due, later));
        when(lock.execute(anyString(), any())).thenAnswer(call -> ((Supplier<Mono<?>>) call.getArgument(1)).get());
        when(outbox.find("due")).thenReturn(Mono.just(due));
        when(delivery.deliver(due)).thenReturn(Mono.just(due));
        worker.retryPending().block();
        verify(delivery).deliver(due);
        verify(lock, never()).execute(eq("later"), any());
    }
}
