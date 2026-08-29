package com.person.repit.interview.dto.response;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowQuestionAiResponseTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void score가_포함된_AI_응답을_역직렬화한다() throws Exception {
        String json = """
                {
                  "score": 2,
                  "required": true,
                  "intention": "답변의 구체성을 확인합니다.",
                  "content": "구체적인 사례를 설명해 주세요."
                }
                """;

        FollowQuestionAiResponse response =
                objectMapper.readValue(json, FollowQuestionAiResponse.class);

        assertThat(response.getScore()).isEqualTo(2);
        assertThat(response.getRequired()).isTrue();
        assertThat(response.getIntention()).isEqualTo("답변의 구체성을 확인합니다.");
        assertThat(response.getContent()).isEqualTo("구체적인 사례를 설명해 주세요.");
    }
}
