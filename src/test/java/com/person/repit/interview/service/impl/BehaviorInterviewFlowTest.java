package com.person.repit.interview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.person.repit.common.config.JacksonConfig;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.service.*;
import com.person.repit.interview.type.*;
import com.person.repit.tts.service.InterviewTtsScheduler;
import com.person.repit.tts.service.PersonaTtsProfileResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.data.redis.serializer.RedisSerializer;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Exercises service -> serialized session -> outbox -> delivery without video/analysis infrastructure. */
@SuppressWarnings("unchecked")
class BehaviorInterviewFlowTest {
    private final ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
    private final ReactiveValueOperations<String, Object> values = mock(ReactiveValueOperations.class);
    private final ReactiveStringRedisTemplate strings = mock(ReactiveStringRedisTemplate.class);
    private final ReactiveValueOperations<String, String> stringValues = mock(ReactiveValueOperations.class);
    private final Map<String, byte[]> sessions = new HashMap<>();
    private final Map<String, String> records = new HashMap<>();
    private final List<String> sent = new ArrayList<>();
    private final RedisSerializer<Object> serializer = RedisSerializer.json();
    private final ApiServerClient api = mock(ApiServerClient.class);
    private final AiQuestionClient ai = mock(AiQuestionClient.class);
    private final InterviewTtsScheduler tts = mock(InterviewTtsScheduler.class);
    private final com.fasterxml.jackson.databind.ObjectMapper mapper = new JacksonConfig().objectMapper();
    private ChatInterviewServiceImpl service;
    private InterviewResultOutbox outbox;
    private InterviewResultDeliveryService delivery;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        when(strings.opsForValue()).thenReturn(stringValues);
        when(values.get(anyString())).thenAnswer(call -> Mono.fromSupplier(() ->
                serializer.deserialize(sessions.get(call.getArgument(0)))));
        when(values.set(anyString(), any(), any(Duration.class))).thenAnswer(call -> saveSession(call));
        when(values.set(anyString(), any())).thenAnswer(call -> saveSession(call));
        when(redis.delete(anyString())).thenAnswer(call -> Mono.fromSupplier(() ->
                sessions.remove(call.getArgument(0)) == null ? 0L : 1L));
        when(stringValues.get(anyString())).thenAnswer(call ->
                Mono.fromSupplier(() -> records.get(call.getArgument(0))));
        when(stringValues.setIfAbsent(anyString(), anyString())).thenAnswer(call ->
                Mono.fromSupplier(() -> records.putIfAbsent(call.getArgument(0), call.getArgument(1)) == null));
        when(stringValues.set(anyString(), anyString())).thenAnswer(call -> Mono.fromSupplier(() -> {
            records.put(call.getArgument(0), call.getArgument(1));
            return true;
        }));
        when(api.saveInterviewPayload(anyString())).thenAnswer(call -> Mono.fromRunnable(() ->
                sent.add(call.getArgument(0))));
        var lock = mock(InterviewOperationLock.class);
        when(lock.execute(anyString(), any())).thenAnswer(call ->
                ((Supplier<Mono<?>>) call.getArgument(1)).get());
        var meters = new SimpleMeterRegistry();
        outbox = new InterviewResultOutbox(strings, mapper);
        delivery = new InterviewResultDeliveryService(outbox, api, new InterviewResultRetryPolicy(), meters);
        service = new ChatInterviewServiceImpl(ai, redis, delivery, lock, new RepitMetrics(meters),
                mock(PersonaTtsProfileResolver.class), tts);
    }

    @ParameterizedTest
    @CsvSource({"SOLO,AUTO", "MULTI,AUTO", "SOLO,COMPLETE", "MULTI,COMPLETE", "SOLO,QUIT", "MULTI,QUIT"})
    void 영상_업로드나_분석없이_종료기록의_식별자를_보존한다(InterviewMode mode, String path) throws Exception {
        prepareSession(mode);
        Mono<ChatProgressResponse> operation = switch (path) {
            case "AUTO" -> service.submitAnswer("api-session", answer());
            case "QUIT" -> service.quitInterview("api-session");
            default -> service.completeInterview("api-session");
        };
        var expected = path.equals("QUIT") ? InterviewStatus.ABANDONED : InterviewStatus.COMPLETED;
        assertThat(operation.block(Duration.ofSeconds(2)).getStatus()).isEqualTo(expected);
        assertThat(sent).hasSize(1);
        var payload = mapper.readTree(sent.getFirst());
        assertIdentity(payload, mode);
        assertThat(payload.path("status").asText()).isEqualTo(expected.name());
        assertThat(payload.path("qnaRequests").get(0).path("question").path("id").asLong()).isEqualTo(303L);
        assertThat(payload.path("qnaRequests").get(0).path("question").path("personaId").asLong()).isEqualTo(404L);
        if (path.equals("AUTO")) {
            assertThat(payload.path("qnaRequests").get(0).path("answer").path("answerContent").asText())
                    .isEqualTo("마지막 답변");
        }
        assertThat(sessions).isEmpty();
        verify(tts).cancelSession("api-session");
        verifyNoInteractions(ai);
        // Duplicate completion / answer / quit all return the original completion status.
        assertThat(service.completeInterview("api-session").block().getStatus()).isEqualTo(expected);
        assertThat(service.quitInterview("api-session").block().getStatus()).isEqualTo(expected);
        assertThat(service.submitAnswer("api-session", answer()).block().getStatus()).isEqualTo(expected);
        verify(api).saveInterviewPayload(anyString());
    }

    @ParameterizedTest
    @EnumSource(InterviewMode.class)
    void API_타임아웃후에도_종료하고_같은_식별자와_최종답변으로_재전송한다(InterviewMode mode) throws Exception {
        prepareSession(mode);
        when(api.saveInterviewPayload(anyString())).thenAnswer(call -> {
            sent.add(call.getArgument(0));
            return sent.size() == 1 ? Mono.error(new TimeoutException()) : Mono.empty();
        });
        assertThat(service.submitAnswer("api-session", answer()).block(Duration.ofSeconds(2)).getStatus())
                .isEqualTo(InterviewStatus.COMPLETED);
        assertThat(sessions).isEmpty();
        var pending = outbox.find("api-session").block();
        assertThat(pending.getState()).isEqualTo(com.person.repit.interview.domain.InterviewResultDelivery.State.PENDING);
        assertIdentity(mapper.readTree(pending.getPayload()), mode);
        assertThat(pending.getPayload()).isEqualTo(sent.getFirst()).contains("마지막 답변");
        assertThat(service.completeInterview("api-session").block().getStatus()).isEqualTo(InterviewStatus.COMPLETED);
        verify(api).saveInterviewPayload(anyString());
        pending.setNextAttemptAt(Instant.EPOCH);
        delivery.deliver(pending).block(Duration.ofSeconds(2));
        assertThat(sent).hasSize(2);
        assertThat(sent.get(1)).isEqualTo(sent.getFirst());
        var restored = outbox.find("api-session").block();
        assertThat(restored.getState()).isEqualTo(com.person.repit.interview.domain.InterviewResultDelivery.State.SENT);
        assertThat(restored.getPayload()).isNull();
        verifyNoInteractions(ai);
    }

    private Mono<Boolean> saveSession(org.mockito.invocation.InvocationOnMock call) {
        return Mono.fromSupplier(() -> {
            sessions.put(call.getArgument(0), serializer.serialize(call.getArgument(1)));
            return true;
        });
    }

    private void prepareSession(InterviewMode mode) {
        var session = ChatInterviewSession.builder().sessionId("api-session").interviewId(101L).userId(202L)
                .mode(mode).createdAt(LocalDateTime.of(2026, 10, 20, 10, 0))
                .questions(new ArrayList<>(List.of(ChatQuestion.builder().questionId(303L)
                        .askedByPersonaId(404L).type(QuestionType.ORIGINAL).content("질문").build())))
                .build();
        sessions.put("chat:interview:api-session", serializer.serialize(session));
    }

    private void assertIdentity(JsonNode payload, InterviewMode mode) {
        assertThat(payload.path("sessionId").asText()).isEqualTo("api-session");
        assertThat(payload.path("interviewId").asLong()).isEqualTo(101L);
        assertThat(payload.path("userId").asLong()).isEqualTo(202L);
        assertThat(payload.path("mode").asText()).isEqualTo(mode.name());
        var fields = new ArrayList<String>();
        payload.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("sessionId", "interviewId", "userId", "status",
                "mode", "interviewCreatedAt", "qnaRequests");
    }

    private ChatAnswerRequest answer() {
        return ChatAnswerRequest.builder().questionId(303L).content("마지막 답변").responseTime(12).build();
    }
}
