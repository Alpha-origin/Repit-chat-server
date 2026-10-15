package com.person.repit.interview.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class ChatInterviewSessionTest {

    private final RedisSerializer<Object> serializer = RedisSerializer.json();

    @Test
    void Redis_직렬화로_기업_컨텍스트를_저장하고_복원한다() {
        var session = ChatInterviewSession.builder().sessionId("s")
                .createdAt(LocalDateTime.of(2026, 10, 15, 10, 0))
                .company(CompanyContext.builder().displayName("샘플커머스")
                        .interviewerBrief("주문 동시성과 오너십을 다루는 샘플기업").build())
                .build();

        var restored = (ChatInterviewSession) serializer.deserialize(serializer.serialize(session));

        assertThat(restored.getSessionId()).isEqualTo("s");
        assertThat(restored.getCreatedAt()).isEqualTo(session.getCreatedAt());
        assertThat(restored.getCompany()).usingRecursiveComparison().isEqualTo(session.getCompany());
    }

    @Test
    void company_필드가_없는_기존_Redis_세션도_읽는다() throws Exception {
        var session = ChatInterviewSession.builder().sessionId("legacy").build();
        var mapper = new ObjectMapper();
        ObjectNode legacy = (ObjectNode) mapper.readTree(serializer.serialize(session));
        legacy.remove("company");
        legacy.put("unknownField", "ignored");

        var restored = (ChatInterviewSession) serializer.deserialize(mapper.writeValueAsBytes(legacy));

        assertThat(restored.getSessionId()).isEqualTo("legacy");
        assertThat(restored.getCompany()).isNull();
    }
}
