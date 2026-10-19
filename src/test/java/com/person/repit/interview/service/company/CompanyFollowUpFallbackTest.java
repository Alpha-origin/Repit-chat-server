package com.person.repit.interview.service.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.reactive.function.client.ClientResponse;
import reactor.core.publisher.Mono;

import static com.person.repit.interview.service.company.CompanyFollowUpTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class CompanyFollowUpFallbackTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "null", "[]", "{}", "not-json", "```json\n{}\n```",
            "{\"intention\":\"i\",\"content\":\"c\",\"expectedAnswer\":\"a\"}",
            "{\"intention\":\"i\",\"content\":42,\"spokenText\":null,\"expectedAnswer\":\"a\"}",
            "{\"intention\":\" \",\"content\":\"c\",\"spokenText\":null,\"expectedAnswer\":\"a\"}",
            "{\"intention\":\"i\",\"content\":\"c\",\"spokenText\":null,\"expectedAnswer\":\"a\"} {}",
            "{\"intention\":\"i\",\"content\":\"first\",\"content\":\"second\",\"spokenText\":null,\"expectedAnswer\":\"a\"}"
    })
    void 기업_응답_오류에도_일반_질문의_모든_필드를_보존한다(String company) {
        var h = harness(enabled(), index -> Mono.just(response(index == 0 ? BASE : company)));
        var result = h.client().decideFollowQuestion(request("기업")).block();
        assertThat(result.getRequired()).isTrue();
        assertThat(result.getScore()).isEqualTo(2);
        assertThat(result.getContent()).isEqualTo("일반 질문?");
        assertThat(result.getIntention()).isEqualTo("선택 근거 확인");
        assertThat(result.getSpokenText()).isEqualTo("일반 질문?");
        assertThat(result.getExpectedAnswer()).isEqualTo("일반 답안");
        assertThat(h.bodies()).hasSize(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"score", "required", "questionId", "parentId", "extra"})
    void 기업_생성에_판단이나_식별자_필드가_추가되면_거부한다(String field) {
        String malicious = COMPANY.strip().replaceFirst("\\{", "{\"" + field + "\":0,");
        var h = harness(enabled(), index -> Mono.just(response(index == 0 ? BASE : malicious)));
        var result = h.client().decideFollowQuestion(request("기업")).block();
        assertThat(result.getScore()).isEqualTo(2);
        assertThat(result.getRequired()).isTrue();
        assertThat(result.getContent()).isEqualTo("일반 질문?");
    }

    @ParameterizedTest
    @ValueSource(strings = {"max_tokens", "tool_use", "refusal", "pause_turn", "", "stop_sequence"})
    void 잘린_JSON이_파싱되거나_종료이유가_다르면_완료로_인정하지_않는다(String stop) {
        var h = harness(enabled(), index -> Mono.just(index == 0 ? response(BASE) : response(COMPANY, stop)));
        assertThat(h.client().decideFollowQuestion(request("기업")).block().getContent()).isEqualTo("일반 질문?");
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 429, 500, 503})
    void 기업_API_오류는_재시도없이_일반_질문으로_돌아간다(int status) {
        var h = harness(enabled(), index -> Mono.just(index == 0 ? response(BASE)
                : ClientResponse.create(HttpStatus.valueOf(status)).body("private data").build()));
        assertThat(h.client().decideFollowQuestion(request("기업")).block().getContent()).isEqualTo("일반 질문?");
        assertThat(h.bodies()).hasSize(2);
    }

    @Test
    void 기본_API_오류는_기존_불필요_폴백을_유지한다() {
        var h = harness(enabled(), index -> Mono.error(new IllegalStateException("unavailable")));
        assertThat(h.client().decideFollowQuestion(request("기업")).block().getRequired()).isFalse();
        assertThat(h.bodies()).hasSize(1);
    }

    @Test
    void 기업_HTTP_응답_자체가_비어도_일반_결과를_유지한다() {
        var h = harness(enabled(), index -> Mono.just(index == 0 ? response(BASE)
                : ClientResponse.create(HttpStatus.OK).build()));
        assertThat(h.client().decideFollowQuestion(request("기업")).block().getContent()).isEqualTo("일반 질문?");
    }
}
