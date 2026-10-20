package com.person.repit.interview.service.company;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeoutException;

import static com.person.repit.interview.service.company.CompanyFollowUpTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class FollowUpDecisionLoggingTest {
    @Test
    void 정상적인_생략은_호출_실패와_구분한다() {
        try (var logs = new CapturedLogs()) {
            var h = harness(enabled(), index -> Mono.just(response("{\"score\":5,\"required\":false}")));
            assertThat(h.client().decideFollowQuestion(request(null)).block().getRequired()).isFalse();
            assertThat(h.bodies()).hasSize(1);
            assertThat(logs.lines()).anySatisfy(line -> assertThat(line)
                    .contains("status=completed", "reason=model_not_required", "sessionId=s", "questionId=4"));
            assertThat(logs.lines()).noneMatch(line -> line.contains("status=fallback"));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "not-json", "{\"required\":true,\"content\":\"비공개 응답\"}"})
    void 잘못된_기본_응답은_정상_생략으로_기록하지_않는다(String body) {
        try (var logs = new CapturedLogs()) {
            var h = harness(enabled(), index -> Mono.just(response(body)));
            assertThat(h.client().decideFollowQuestion(request(null)).block().getRequired()).isFalse();
            assertThat(h.bodies()).hasSize(1);
            assertThat(logs.lines()).anySatisfy(line -> assertThat(line)
                    .contains("status=fallback", "reason=invalid_response", "interviewId=1", "questionId=4"));
            assertThat(logs.lines()).noneMatch(line -> line.contains("reason=model_not_required"));
            assertThat(String.join("\n", logs.lines())).doesNotContain("비공개 응답", "구현했습니다");
        }
    }

    @Test
    void 빈_HTTP_본문도_면접_진행을_위한_불필요_결과를_반환한다() {
        try (var logs = new CapturedLogs()) {
            var h = harness(enabled(), index -> Mono.just(ClientResponse.create(HttpStatus.OK).build()));
            assertThat(h.client().decideFollowQuestion(request(null)).block()).isNotNull()
                    .satisfies(result -> assertThat(result.getRequired()).isFalse());
            assertThat(logs.lines()).anyMatch(line -> line.contains("reason=invalid_response"));
        }
    }

    @Test
    void 시간초과와_동시요청_제한을_구분하고_원문은_로그에_남기지_않는다() {
        try (var logs = new CapturedLogs()) {
            var timeout = harness(enabled(), index -> Mono.error(new TimeoutException("비공개 예외")));
            var unavailable = harness(enabled(), index -> Mono.error(new RejectedExecutionException("비공개 예외")));
            assertThat(timeout.client().decideFollowQuestion(request(null)).block().getRequired()).isFalse();
            assertThat(unavailable.client().decideFollowQuestion(request(null)).block().getRequired()).isFalse();
            assertThat(logs.lines()).anyMatch(line -> line.contains("reason=ai_timeout"));
            assertThat(logs.lines()).anyMatch(line -> line.contains("reason=ai_unavailable"));
            assertThat(String.join("\n", logs.lines())).doesNotContain("비공개 예외");
        }
    }

    @Test
    void 기업_재작성_실패는_원래_질문을_유지하고_식별자를_기록한다() {
        try (var logs = new CapturedLogs()) {
            var h = harness(enabled(), index -> Mono.just(response(index == 0 ? BASE : "비공개 기업 응답")));
            assertThat(h.client().decideFollowQuestion(request("비공개 기업")).block().getContent())
                    .isEqualTo("일반 질문?");
            assertThat(logs.lines()).anySatisfy(line -> assertThat(line)
                    .contains("follow_up.company status=fallback", "reason=generation_failed",
                            "sessionId=s", "interviewId=1", "questionId=4"));
            assertThat(String.join("\n", logs.lines())).doesNotContain("비공개 기업");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"timeout", "empty", "invalid"})
    void 기업_폴백을_생성_성공으로_기록하지_않는다(String failure) {
        try (var logs = new CapturedLogs()) {
            var h = harness(enabled(), index -> index == 0 ? Mono.just(response(BASE)) : switch (failure) {
                case "timeout" -> Mono.error(new TimeoutException());
                case "empty" -> Mono.just(ClientResponse.create(HttpStatus.OK).build());
                default -> Mono.just(response("invalid"));
            });
            assertThat(h.client().decideFollowQuestion(request("기업")).block().getContent())
                    .isEqualTo("일반 질문?");
            String reason = switch (failure) {
                case "timeout" -> "company_timeout";
                case "empty" -> "company_empty_response";
                default -> "company_generation_failed";
            };
            assertThat(logs.lines()).anySatisfy(line -> assertThat(line)
                    .contains("follow_up.decision status=fallback", "reason=" + reason));
            assertThat(logs.lines()).noneMatch(line -> line.contains("follow_up.decision status=completed"));
        }
    }

    @Test
    void 기업_재작성_성공만_생성_성공으로_기록한다() {
        try (var logs = new CapturedLogs()) {
            var h = harness(enabled(), index -> Mono.just(response(index == 0 ? BASE : COMPANY)));
            assertThat(h.client().decideFollowQuestion(request("기업")).block().getContent())
                    .isEqualTo("기업 관점 질문?");
            assertThat(logs.lines()).anySatisfy(line -> assertThat(line)
                    .contains("follow_up.decision status=completed reason=generated"));
            assertThat(logs.lines()).noneMatch(line -> line.contains("status=fallback"));
        }
    }

    private static final class CapturedLogs implements AutoCloseable {
        private final Logger logger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

        private CapturedLogs() {
            appender.start();
            logger.addAppender(appender);
        }

        private List<String> lines() {
            return appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(line -> line.startsWith("follow_up.")).toList();
        }

        @Override
        public void close() {
            logger.detachAppender(appender);
            appender.stop();
        }
    }
}
