package com.person.repit.interview.service.company;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import static com.person.repit.interview.service.company.CompanyFollowUpTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class CompanyFollowUpLoggingTest {
    @Test
    void 단계별_토큰과_호출횟수를_기록하고_원문은_남기지_않는다() {
        Logger root = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
        var appender = new ListAppender<ILoggingEvent>();
        appender.start();
        root.addAppender(appender);
        try {
            var harness = harness(enabled(), index -> Mono.just(response(index == 0 ? BASE : "비공개 모델 응답")));
            var result = harness.client().decideFollowQuestion(request("비공개 기업명")).block();
            assertThat(result.getRequired()).isTrue();
            var logs = appender.list.stream().map(ILoggingEvent::getFormattedMessage)
                    .filter(line -> line.startsWith("follow_up.")).toList();
            assertThat(logs.stream().filter(line -> line.startsWith("follow_up.call"))).hasSize(2);
            assertThat(logs).anySatisfy(line -> assertThat(line).contains("phase=A", "input_tokens=11", "output_tokens=17", "call_count=1"));
            assertThat(logs).anySatisfy(line -> assertThat(line).contains("phase=B", "input_tokens=11", "output_tokens=17", "call_count=1"));
            assertThat(logs).anySatisfy(line -> assertThat(line).contains("status=fallback", "reason=generation_failed"));
            assertThat(String.join("\n", logs)).doesNotContain("비공개 기업명", "주문 동시성과 고객 관점", "구현했습니다", "비공개 모델 응답");
        } finally {
            root.detachAppender(appender);
            appender.stop();
        }
    }
}
