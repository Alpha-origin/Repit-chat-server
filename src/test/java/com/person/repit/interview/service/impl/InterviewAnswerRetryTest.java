package com.person.repit.interview.service.impl;

import com.person.repit.common.exception.InvalidRequestException;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
import com.person.repit.interview.exception.InterviewQuestionMismatchException;
import com.person.repit.interview.service.*;
import com.person.repit.interview.type.*;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.service.InterviewTtsScheduler;
import com.person.repit.tts.service.PersonaTtsProfileResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class InterviewAnswerRetryTest {
    private final ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
    private final ReactiveValueOperations<String, Object> values = mock(ReactiveValueOperations.class);
    private final AiQuestionClient ai = mock(AiQuestionClient.class);
    private final InterviewTtsScheduler tts = mock(InterviewTtsScheduler.class);
    private final RedisSerializer<Object> serializer = RedisSerializer.json();
    private final AtomicReference<byte[]> stored = new AtomicReference<>();
    private ChatInterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(values.get("chat:interview:s")).thenAnswer(call ->
                Mono.fromSupplier(() -> serializer.deserialize(stored.get())));
        when(values.set(anyString(), any(), any(Duration.class))).thenAnswer(call -> Mono.fromSupplier(() -> {
            stored.set(serializer.serialize(call.getArgument(1)));
            return true;
        }));
        when(ai.decideFollowQuestion(any())).thenReturn(Mono.just(FollowQuestionAiResponse.notRequired()));
        when(tts.register(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(AudioState.builder().build()));
        var lock = mock(InterviewOperationLock.class);
        when(lock.execute(anyString(), any())).thenAnswer(call -> ((Supplier<Mono<?>>) call.getArgument(1)).get());
        var delivery = mock(InterviewResultDeliveryService.class);
        when(delivery.resume(anyString())).thenReturn(Mono.empty());
        service = new ChatInterviewServiceImpl(ai, redis, delivery, lock,
                new RepitMetrics(new SimpleMeterRegistry()), mock(PersonaTtsProfileResolver.class), tts);
        var questions = new ArrayList<ChatQuestion>();
        for (long id = 1; id <= 3; id++) {
            questions.add(ChatQuestion.builder().questionId(id).content("질문 " + id)
                    .type(QuestionType.ORIGINAL).askedByPersonaId(4L).build());
        }
        stored.set(serializer.serialize(ChatInterviewSession.builder().sessionId("s").interviewId(1L).userId(2L)
                .personality(InterviewType.FRIENDLY).tone(InterviewTone.GENTLE)
                .major(InterviewMajor.BACKEND).level(InterviewLevel.NORMAL).questions(questions).build()));
    }

    @Test
    void 이미_접수한_답변_재요청은_현재_질문을_반환하고_부수효과를_반복하지_않는다() {
        assertThat(service.submitAnswer("s", answer(1L)).block().getQuestion().getQuestionId()).isEqualTo(2L);
        byte[] original = stored.get().clone();
        clearInvocations(values, ai, tts);
        assertThat(service.submitAnswer("s", answer(1L)).block().getQuestion().getQuestionId()).isEqualTo(2L);
        assertThat(stored.get()).isEqualTo(original);
        verify(values, never()).set(anyString(), any(), any(Duration.class));
        verifyNoInteractions(ai, tts);
    }

    @Test
    void 오래된_답변의_재전송도_질문을_되돌리지_않는다() {
        service.submitAnswer("s", answer(1L)).block();
        service.submitAnswer("s", answer(2L)).block();
        clearInvocations(ai, tts);
        assertThat(service.submitAnswer("s", answer(1L)).block().getQuestion().getQuestionId()).isEqualTo(3L);
        assertThat(session().getAnswers()).hasSize(2);
        assertThat(session().getCurrentQuestionIndex()).isEqualTo(2);
        verifyNoInteractions(ai, tts);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void 같은_질문에_다른_내용이나_응답시간을_보내면_거절한다(boolean changeContent) {
        service.submitAnswer("s", answer(1L)).block();
        var changed = ChatAnswerRequest.builder().questionId(1L)
                .content(changeContent ? "다른 답변" : "접수 답변").responseTime(changeContent ? 10 : 11).build();
        StepVerifier.create(service.submitAnswer("s", changed))
                .expectErrorSatisfies(error -> assertThat(error).isInstanceOf(InvalidRequestException.class)
                        .hasMessage("이미 접수한 답변과 다릅니다."))
                .verify(Duration.ofSeconds(2));
        assertThat(session().getAnswers()).hasSize(1);
    }

    @Test
    void 꼬리질문을_생성한_원답변_재요청은_같은_꼬리질문을_반환한다() {
        var follow = new FollowQuestionAiResponse();
        follow.setRequired(true);
        follow.setContent("꼬리질문");
        follow.setExpectedAnswer("참고 답안");
        when(ai.decideFollowQuestion(any())).thenReturn(Mono.just(follow));
        assertThat(service.submitAnswer("s", answer(1L)).block().getQuestion().getQuestionId()).isEqualTo(-1L);
        clearInvocations(ai, tts);
        assertThat(service.submitAnswer("s", answer(1L)).block().getQuestion().getQuestionId()).isEqualTo(-1L);
        assertThat(session().getQuestions()).hasSize(4);
        verifyNoInteractions(ai, tts);
        assertThat(service.submitAnswer("s", answer(-1L)).block().getQuestion().getQuestionId()).isEqualTo(2L);
        clearInvocations(ai, tts);
        assertThat(service.submitAnswer("s", answer(-1L)).block().getQuestion().getQuestionId()).isEqualTo(2L);
        verifyNoInteractions(ai, tts);
    }

    @Test
    void 답변저장후_AI대기중_요청취소는_같은_답변으로_진행을_재개한다() {
        var decision = Sinks.<FollowQuestionAiResponse>one();
        when(ai.decideFollowQuestion(any())).thenReturn(decision.asMono());
        var pending = service.submitAnswer("s", answer(1L)).subscribe();
        var acceptedAt = session().getAnswers().getFirst().getCreatedAt();
        pending.dispose();
        when(ai.decideFollowQuestion(any())).thenReturn(Mono.just(FollowQuestionAiResponse.notRequired()));
        assertThat(service.submitAnswer("s", answer(1L)).block().getQuestion().getQuestionId()).isEqualTo(2L);
        assertThat(session().getAnswers()).hasSize(1);
        assertThat(session().getAnswers().getFirst().getCreatedAt()).isEqualTo(acceptedAt);
        verify(ai, times(2)).decideFollowQuestion(any());
    }

    @Test
    void 접수한_적_없는_다른_질문의_답변은_계속_거절한다() {
        StepVerifier.create(service.submitAnswer("s", answer(99L)))
                .expectError(InterviewQuestionMismatchException.class).verify(Duration.ofSeconds(2));
        assertThat(session().getAnswers()).isEmpty();
        verifyNoInteractions(ai, tts);
    }

    private ChatInterviewSession session() {
        return (ChatInterviewSession) serializer.deserialize(stored.get());
    }

    private ChatAnswerRequest answer(long questionId) {
        return ChatAnswerRequest.builder().questionId(questionId).content("접수 답변").responseTime(10).build();
    }
}
