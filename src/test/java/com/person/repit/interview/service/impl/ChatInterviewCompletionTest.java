package com.person.repit.interview.service.impl;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.common.exception.handler.GlobalExceptionHandler;
import com.person.repit.interview.controller.ChatInterviewController;
import com.person.repit.interview.domain.ChatAnswer;
import com.person.repit.interview.exception.InterviewNotFinishedException;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.service.*;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.QuestionType;
import com.person.repit.tts.service.InterviewTtsScheduler;
import com.person.repit.tts.service.PersonaTtsProfileResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ChatInterviewCompletionTest {
    private final ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
    private final ReactiveValueOperations<String, Object> values = mock(ReactiveValueOperations.class);
    private final InterviewResultDeliveryService delivery = mock(InterviewResultDeliveryService.class);
    private final InterviewOperationLock lock = mock(InterviewOperationLock.class);
    private final InterviewTtsScheduler tts = mock(InterviewTtsScheduler.class);
    private final AtomicBoolean finalSaved = new AtomicBoolean();
    private ChatInterviewSession session;
    private ChatInterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(lock.execute(anyString(), any())).thenAnswer(call -> ((Supplier<Mono<?>>) call.getArgument(1)).get());
        when(delivery.resume("s")).thenReturn(Mono.empty());
        session = ChatInterviewSession.builder().sessionId("s").interviewId(1L).userId(2L)
                .questions(new ArrayList<>(List.of(ChatQuestion.builder().questionId(1L)
                        .type(QuestionType.ORIGINAL).content("질문").build())))
                .build();
        when(values.get("chat:interview:s")).thenReturn(Mono.just(session));
        when(values.set(eq("chat:interview:s"), any(), any(Duration.class))).thenReturn(Mono.just(true));
        when(values.set(eq("chat:interview:s"), any())).thenAnswer(call ->
                Mono.fromSupplier(() -> { finalSaved.set(true); return true; }));
        when(redis.delete("chat:interview:s")).thenReturn(Mono.just(1L));
        when(delivery.submit(any())).thenAnswer(call -> {
            assertThat(finalSaved).isTrue();
            ChatInterviewAllRequest payload = call.getArgument(0);
            return Mono.just(payload.getStatus() == InterviewStatus.ABANDONED
                    ? ChatProgressResponse.quit() : ChatProgressResponse.end());
        });
        service = new ChatInterviewServiceImpl(mock(AiQuestionClient.class), redis, delivery, lock,
                new RepitMetrics(new SimpleMeterRegistry()), mock(PersonaTtsProfileResolver.class), tts);
    }

    @Test
    void 마지막답변_자동종료는_답변을_먼저_저장하고_최종본문에_포함한다() {
        var result = service.submitAnswer("s", answer()).block();
        assertThat(result.getStatus()).isEqualTo(InterviewStatus.COMPLETED);
        var captor = org.mockito.ArgumentCaptor.forClass(ChatInterviewAllRequest.class);
        verify(delivery).submit(captor.capture());
        assertThat(captor.getValue().getQnaRequests().getFirst().getAnswer().getAnswerContent())
                .isEqualTo("마지막 답변");
        verify(tts).cancelSession("s");
    }

    @Test
    void 일반완료와_중단은_동일한_보존경로를_사용한다() {
        assertThat(service.quitInterview("s").block().getStatus()).isEqualTo(InterviewStatus.ABANDONED);
        verify(delivery).submit(argThat(payload -> payload.getStatus() == InterviewStatus.ABANDONED));
    }

    @Test
    void 일반완료도_최종기록을_보존한다() {
        session.setCurrentQuestionIndex(-1);
        assertThat(service.completeInterview("s").block().getStatus()).isEqualTo(InterviewStatus.COMPLETED);
        verify(delivery).submit(argThat(payload -> payload.getStatus() == InterviewStatus.COMPLETED));
    }

    @Test
    void 대기등록에_실패하면_마지막답변과_세션을_보존하고_정리하지_않는다() {
        doReturn(Mono.error(new IllegalStateException("outbox down"))).when(delivery).submit(any());
        StepVerifier.create(service.submitAnswer("s", answer())).expectError(IllegalStateException.class).verify();
        assertThat(session.getAnswers()).hasSize(1);
        assertThat(finalSaved).isTrue();
        verify(redis, never()).delete(anyString());
        verify(tts).cancelSession("s");
        doReturn(Mono.just(ChatProgressResponse.end())).when(delivery).submit(any());
        service.submitAnswer("s", answer()).block();
        assertThat(session.getAnswers()).hasSize(1);
    }

    @Test
    void 최종기록_저장실패는_성공처리하지_않는다() {
        session.setCurrentQuestionIndex(-1);
        when(values.set(eq("chat:interview:s"), any())).thenReturn(Mono.just(false));
        StepVerifier.create(service.completeInterview("s")).expectError(IllegalStateException.class).verify();
        verify(delivery, never()).submit(any());
        verify(redis, never()).delete(anyString());
        verifyNoInteractions(tts);
    }

    @Test
    void 이미_접수한_중복종료는_세션이나_답변을_다시_만들지_않는다() {
        when(delivery.resume("s")).thenReturn(Mono.just(ChatProgressResponse.end()));
        service.submitAnswer("s", answer()).block();
        service.completeInterview("s").block();
        service.quitInterview("s").block();
        verify(values, never()).get(anyString());
        verify(delivery, never()).submit(any());
    }

    @Test
    void 조기_완료는_REST_409로_거절하고_뒤이은_중단은_허용한다() {
        var client = WebTestClient.bindToController(new ChatInterviewController(service))
                .controllerAdvice(new GlobalExceptionHandler()).build();
        client.post().uri("/chat/interviews/s/complete").exchange()
                .expectStatus().isEqualTo(409).expectBody()
                .jsonPath("$.code").isEqualTo("INTERVIEW_NOT_FINISHED");
        assertThat(finalSaved).isFalse();
        assertThat(session.getStatus()).isEqualTo(InterviewStatus.IN_PROGRESS);
        verify(delivery, never()).submit(any());
        verifyNoInteractions(tts);
        client.post().uri("/chat/interviews/s/quit").exchange()
                .expectStatus().isOk().expectBody().jsonPath("$.status").isEqualTo("ABANDONED");
    }

    @ParameterizedTest
    @ValueSource(longs = {2L, -1L})
    void 답변이_있어도_다음_원질문이나_꼬리질문이_남으면_완료하지_않는다(long nextId) {
        session.getQuestions().add(ChatQuestion.builder().questionId(nextId)
                .type(nextId < 0 ? QuestionType.FOLLOW : QuestionType.ORIGINAL).build());
        session.getAnswers().add(ChatAnswer.builder().questionId(1L).content("기존 답변").build());
        session.setCurrentQuestionIndex(1);
        StepVerifier.create(service.completeInterview("s")).expectError(InterviewNotFinishedException.class).verify();
        assertThat(finalSaved).isFalse();
        assertThat(session.getCurrentQuestionIndex()).isEqualTo(1);
        verify(delivery, never()).submit(any());
        verifyNoInteractions(tts);
    }

    @ParameterizedTest
    @EnumSource(value = InterviewStatus.class, names = {"COMPLETED", "ABANDONED"})
    void 대기_등록_전_최종_세션도_반대_종료나_늦은_답변으로_바꾸지_않는다(InterviewStatus status) {
        session.setStatus(status);
        assertThat(service.completeInterview("s").block().getStatus()).isEqualTo(status);
        assertThat(service.quitInterview("s").block().getStatus()).isEqualTo(status);
        assertThat(service.submitAnswer("s", answer()).block().getStatus()).isEqualTo(status);
        assertThat(session.getAnswers()).isEmpty();
        assertThat(session.getCurrentQuestionIndex()).isZero();
        verify(delivery, times(3)).submit(argThat(payload -> payload.getStatus() == status));
    }

    @Test
    void 중단은_최종_저장_후_API_전송이_끝나기_전에_TTS를_정지한다() {
        doReturn(Mono.never()).when(delivery).submit(any());
        StepVerifier.create(service.quitInterview("s"))
                .then(() -> {
                    assertThat(finalSaved).isTrue();
                    assertThat(session.getStatus()).isEqualTo(InterviewStatus.ABANDONED);
                    verify(tts).cancelSession("s");
                    verify(redis, never()).delete(anyString());
                }).thenCancel().verify();
    }

    @Test
    void 중단_대기등록_실패_후_늦은_완료요청도_중단_기록을_재전달한다() {
        session.getAnswers().add(ChatAnswer.builder().questionId(1L).content("접수한 답변").build());
        doReturn(Mono.error(new IllegalStateException("outbox down"))).when(delivery).submit(any());
        StepVerifier.create(service.quitInterview("s")).expectError(IllegalStateException.class).verify();
        verify(tts).cancelSession("s");
        verify(redis, never()).delete(anyString());
        doReturn(Mono.just(ChatProgressResponse.quit())).when(delivery).submit(any());
        assertThat(service.completeInterview("s").block().getStatus()).isEqualTo(InterviewStatus.ABANDONED);
        verify(delivery, times(2)).submit(argThat(payload -> payload.getStatus() == InterviewStatus.ABANDONED
                && payload.getQnaRequests().getFirst().getAnswer().getAnswerContent().equals("접수한 답변")));
    }

    private ChatAnswerRequest answer() {
        return ChatAnswerRequest.builder().questionId(1L).content("마지막 답변").responseTime(12).build();
    }
}
