package com.person.repit.interview.service;

import com.person.repit.common.config.JacksonConfig;
import com.person.repit.common.config.RedisConfig;
import com.person.repit.common.exception.AlreadyUsingException;
import com.person.repit.interview.domain.ChatAnswer;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.domain.InterviewResultDelivery;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.tts.service.InterviewTtsScheduler;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;
import reactor.util.retry.Retry;

import java.io.IOException;
import java.net.ServerSocket;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class InterviewResultRedisTest {
    @TempDir Path data;
    private Process process;
    private LettuceConnectionFactory factory;
    private ReactiveStringRedisTemplate redis;
    private InterviewResultOutbox outbox;
    private InterviewOperationLock lock;
    private int port;

    @BeforeEach
    void startRedis() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        try {
            startProcess();
        } catch (IOException missingRedis) {
            Assumptions.abort("redis-server가 설치된 환경에서 실행하는 통합 테스트");
        }
        factory = new LettuceConnectionFactory("127.0.0.1", port);
        factory.afterPropertiesSet();
        factory.start();
        redis = new ReactiveStringRedisTemplate(factory);
        connect();
        outbox = new InterviewResultOutbox(redis, new JacksonConfig().objectMapper());
        lock = new InterviewOperationLock(redis);
    }

    private void startProcess() throws IOException {
        process = new ProcessBuilder("redis-server", "--bind", "127.0.0.1", "--port", String.valueOf(port),
                "--dir", data.toString(), "--appendonly", "yes", "--appendfsync", "always", "--save", "")
                .redirectErrorStream(true).redirectOutput(data.resolve("redis.log").toFile()).start();
    }

    private void connect() {
        Mono.defer(() -> redis.hasKey("readiness-probe"))
                .retryWhen(Retry.fixedDelay(30, Duration.ofMillis(50))).block(Duration.ofSeconds(10));
    }

    @AfterEach
    void stopRedis() throws Exception {
        if (factory != null) factory.destroy();
        if (process != null) {
            process.destroy();
            process.waitFor();
        }
    }

    @Test
    void Redis_강제종료후에도_전송대기와_마지막답변을_복원한다() throws Exception {
        var session = session();
        var first = outbox.enqueue(ChatInterviewAllRequest.from(session)).block();
        var ttl = new org.springframework.data.redis.core.script.DefaultRedisScript<Long>(
                "return redis.call('TTL', KEYS[1])", Long.class);
        assertThat(redis.execute(ttl, List.of("chat:interview:result:s")).single().block()).isEqualTo(-1L);
        process.destroyForcibly().waitFor();
        startProcess();
        connect();
        // New repository instance simulates application restart; no in-memory queue participates.
        var restored = new InterviewResultOutbox(redis, new JacksonConfig().objectMapper()).find("s").block();
        assertThat(restored.getPayload()).isEqualTo(first.getPayload()).contains("마지막 답변");
        assertThat(outbox.findPending().collectList().block()).hasSize(1);
        ApiServerClient client = mock(ApiServerClient.class);
        when(client.saveInterviewPayload(restored.getPayload())).thenReturn(Mono.empty());
        var delivery = new InterviewResultDeliveryService(outbox, client, new InterviewResultRetryPolicy(),
                new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        lock.execute("s", () -> delivery.deliver(restored)).block();
        assertThat(outbox.find("s").block().getState()).isEqualTo(InterviewResultDelivery.State.SENT);
    }

    @Test
    void 중복등록은_최초_식별자와_질문과_답변시간을_유지한다() {
        var session = session();
        String first = outbox.enqueue(ChatInterviewAllRequest.from(session)).block().getPayload();
        session.getAnswers().getFirst().setContent("변경된 답변");
        String second = outbox.enqueue(ChatInterviewAllRequest.from(session)).block().getPayload();
        assertThat(second).isEqualTo(first);
        assertThat(outbox.findPending().collectList().block()).hasSize(1);
    }

    @Test
    void 워커경합과_오래된_잠금해제는_다른_작업의_잠금을_훼손하지_않는다() {
        String key = "chat:interview:operation-lock:s";
        var acquired = reactor.core.publisher.Sinks.<Boolean>one();
        var finish = reactor.core.publisher.Sinks.<String>one();
        var running = lock.execute("s", () -> {
            acquired.tryEmitValue(true);
            return finish.asMono();
        }).toFuture();
        acquired.asMono().block(Duration.ofSeconds(5));
        assertThat(redis.opsForValue().get(key).block()).isNotBlank();
        StepVerifier.create(new InterviewOperationLock(redis).execute("s", () -> Mono.just("second")))
                .expectError(AlreadyUsingException.class).verify();
        String replacement = UUID.randomUUID().toString();
        redis.opsForValue().set(key, replacement, Duration.ofMinutes(2)).block();
        finish.tryEmitValue("finished");
        assertThat(running.join()).isEqualTo("finished");
        assertThat(redis.opsForValue().get(key).block()).isEqualTo(replacement);
        redis.delete(key).block();
        assertThat(lock.execute("s", () -> Mono.just("recovered")).block()).isEqualTo("recovered");
    }

    @Test
    void 최종세션_저장직후_장애가_나도_복구워커가_전송대기를_등록한다() {
        var objects = new RedisConfig().reactiveRedisTemplate(factory);
        objects.opsForValue().set("chat:interview:s", session()).block();
        var recovery = new FinalizedInterviewRecovery(objects, outbox, lock, mock(InterviewTtsScheduler.class));
        recovery.recover().block();
        assertThat(outbox.find("s").block().getPayload()).contains("마지막 답변");
        assertThat(objects.hasKey("chat:interview:s").block()).isFalse();
    }

    private ChatInterviewSession session() {
        return ChatInterviewSession.builder().sessionId("s").interviewId(1L).userId(2L)
                .status(InterviewStatus.COMPLETED).createdAt(LocalDateTime.of(2026, 10, 15, 10, 0))
                .questions(List.of(ChatQuestion.builder().questionId(17L).content("질문").build()))
                .answers(List.of(ChatAnswer.builder().questionId(17L).content("마지막 답변")
                        .createdAt(LocalDateTime.of(2026, 10, 15, 10, 1)).build()))
                .build();
    }
}
