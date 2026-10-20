package com.person.repit.interview.service;

import com.person.repit.common.config.JacksonConfig;
import com.person.repit.interview.dto.request.ChatInterviewAllRequest;
import com.person.repit.interview.type.InterviewStatus;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.test.StepVerifier;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class InterviewResultOutboxTest {
    @ParameterizedTest
    @NullSource
    @EnumSource(value = InterviewStatus.class, names = "IN_PROGRESS")
    void 명시적_종료상태가_없으면_신규_outbox를_등록하지_않는다(InterviewStatus status) {
        var redis = mock(ReactiveStringRedisTemplate.class);
        var outbox = new InterviewResultOutbox(redis, new JacksonConfig().objectMapper());
        var request = ChatInterviewAllRequest.builder().sessionId("s").status(status).build();
        StepVerifier.create(outbox.enqueue(request)).expectError(IllegalArgumentException.class).verify();
        verifyNoInteractions(redis);
    }
}
