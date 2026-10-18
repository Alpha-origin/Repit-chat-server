package com.person.repit.interview.service.company;

import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static com.person.repit.interview.service.company.CompanyFollowUpTestSupport.*;
import static org.assertj.core.api.Assertions.*;

class CompanyFollowUpContentTest {
    @Test
    void 최대_길이는_UTF16이_아닌_유니코드_문자수로_검사한다() {
        var result = new CompanyFollowUpContent("😀".repeat(200), "😀".repeat(200),
                "😀".repeat(1000), "😀".repeat(1000));
        assertThat(result.content().codePointCount(0, result.content().length())).isEqualTo(200);
    }

    @ParameterizedTest
    @ValueSource(strings = {"intention", "content", "spokenText", "expectedAnswer"})
    void 필드별_길이초과는_잘라서_사용하지_않는다(String field) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(COMPANY);
        node.put(field, "😀".repeat(field.equals("intention") || field.equals("content") ? 201 : 1001));
        assertThatIllegalArgumentException().isThrownBy(() -> CompanyFollowUpContent.read(node));
    }

    @ParameterizedTest
    @ValueSource(strings = {"intention", "content", "expectedAnswer"})
    void 필수_내용의_공백과_null은_거부한다(String field) throws Exception {
        var node = (com.fasterxml.jackson.databind.node.ObjectNode) MAPPER.readTree(COMPANY);
        node.putNull(field);
        assertThatIllegalArgumentException().isThrownBy(() -> CompanyFollowUpContent.read(node));
        node.put(field, " \n\t");
        assertThatIllegalArgumentException().isThrownBy(() -> CompanyFollowUpContent.read(node));
    }

    @Test
    void 음성_문장이_없으면_기존_TTS_대체_경로로_보낸다() {
        assertThat(new CompanyFollowUpContent(" i ", " c ", null, " a ").spokenText()).isNull();
        var content = new CompanyFollowUpContent(" i ", " c ", " \n\t", " a ");
        assertThat(content.spokenText()).isNull();
        assertThat(content.content()).isEqualTo("c");
    }

    @Test
    void 기본_객체는_변경하지_않고_점수와_필요여부를_새_결과에_복사한다() throws Exception {
        var decision = MAPPER.readValue(BASE, FollowQuestionAiResponse.class);
        var content = CompanyFollowUpContent.read(MAPPER.readTree(COMPANY));
        var merged = content.merge(decision);
        assertThat(merged).isNotSameAs(decision);
        assertThat(merged.getScore()).isEqualTo(decision.getScore());
        assertThat(merged.getRequired()).isEqualTo(decision.getRequired());
        assertThat(merged.getContent()).isEqualTo(content.content());
        assertThat(decision.getContent()).isEqualTo("일반 질문?");
        assertThat(decision.getIntention()).isEqualTo("선택 근거 확인");
        assertThat(decision.getSpokenText()).isEqualTo("일반 질문?");
        assertThat(decision.getExpectedAnswer()).isEqualTo("일반 답안");
    }
}
