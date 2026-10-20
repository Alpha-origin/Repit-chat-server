package com.person.repit.interview.service;

import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.InterviewResultDelivery;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.tts.service.InterviewTtsScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class FinalizedInterviewRecoveryTest {
    private final ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
    private final ReactiveValueOperations<String, Object> values = mock(ReactiveValueOperations.class);
    private final InterviewResultOutbox outbox = mock(InterviewResultOutbox.class);
    private final InterviewOperationLock lock = mock(InterviewOperationLock.class);
    private final InterviewTtsScheduler tts = mock(InterviewTtsScheduler.class);
    private final FinalizedInterviewRecovery recovery = new FinalizedInterviewRecovery(redis, outbox, lock, tts);
    private ChatInterviewSession terminal;

    @BeforeEach
    void setUp() {
        terminal = ChatInterviewSession.builder().sessionId("s").interviewId(1L).userId(2L)
                .status(InterviewStatus.ABANDONED).build();
        when(redis.opsForValue()).thenReturn(values);
        when(redis.scan(any())).thenReturn(Flux.just("chat:interview:s"));
        when(values.get("chat:interview:s")).thenReturn(Mono.just(terminal));
        when(lock.execute(anyString(), any())).thenAnswer(call -> ((Supplier<Mono<?>>) call.getArgument(1)).get());
        when(outbox.find("s")).thenReturn(Mono.empty());
        when(redis.delete("chat:interview:s")).thenReturn(Mono.just(1L));
    }

    @Test
    void 대기등록이_실패해도_최종세션을_남기고_TTS는_즉시_정리한다() {
        when(outbox.enqueue(any())).thenReturn(Mono.error(new IllegalStateException("redis down")));
        recovery.recover().block();
        verify(tts).cancelSession("s");
        verify(redis, never()).delete(anyString());
        var delivery = new InterviewResultDelivery();
        delivery.setSessionId("s");
        delivery.setInterviewStatus(InterviewStatus.ABANDONED);
        when(outbox.enqueue(any())).thenReturn(Mono.just(delivery));
        recovery.recover().block();
        verify(outbox, times(2)).enqueue(argThat(request -> request.getStatus() == InterviewStatus.ABANDONED));
        verify(redis).delete("chat:interview:s");
        assertThat(terminal.getStatus()).isEqualTo(InterviewStatus.ABANDONED);
    }

    @Test
    void 잠금_획득후_다시_읽은_세션이_진행중이면_복구하지_않는다() {
        var active = ChatInterviewSession.builder().sessionId("s").build();
        when(values.get("chat:interview:s")).thenReturn(Mono.just(terminal), Mono.just(active));
        recovery.recover().block();
        verifyNoInteractions(outbox, tts);
        verify(redis, never()).delete(anyString());
    }
}
