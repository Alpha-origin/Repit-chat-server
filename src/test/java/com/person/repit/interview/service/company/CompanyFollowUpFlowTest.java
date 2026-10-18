package com.person.repit.interview.service.company;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import static com.person.repit.interview.service.company.CompanyFollowUpTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

class CompanyFollowUpFlowTest {
    @Test
    void 기업_질문을_사용하되_기본_점수와_필요여부는_유지한다() {
        var h = harness(enabled(), index -> Mono.just(response(index == 0 ? BASE : COMPANY)));
        StepVerifier.create(h.client().decideFollowQuestion(request("샘플기업")))
                .assertNext(result -> {
                    assertThat(result.getScore()).isEqualTo(2);
                    assertThat(result.getRequired()).isTrue();
                    assertThat(result.getContent()).isEqualTo("기업 관점 질문?");
                    assertThat(result.getExpectedAnswer()).isEqualTo("실제로 확인한 근거");
                }).verifyComplete();
        assertThat(h.bodies()).hasSize(2);
        assertThat(h.bodies().get(0).toString()).doesNotContain("샘플기업", "기업 컨텍스트", "주문 동시성과 고객 관점");
        assertThat(h.bodies().get(1).toString()).contains("샘플기업", "주문 동시성과 고객 관점");
        String user = h.bodies().get(1).get("messages").get(0).get("content").asText();
        assertThat(user).doesNotContain("\"score\"", "\"required\"");
    }

    @Test
    void 서로_다른_기업도_같은_기본_판단_요청을_보낸다() {
        var a = harness(enabled(), index -> Mono.just(response(index == 0 ? BASE : COMPANY)));
        var b = harness(enabled(), index -> Mono.just(response(index == 0 ? BASE : COMPANY)));
        a.client().decideFollowQuestion(request("기업A")).block();
        b.client().decideFollowQuestion(request("기업B")).block();
        assertThat(a.bodies().getFirst()).isEqualTo(b.bodies().getFirst());
        assertThat(a.bodies().get(1)).isNotEqualTo(b.bodies().get(1));
    }

    @Test
    void 기업이_실제_질문에_있으면_원문은_삭제하지_않는다() {
        var r = request("샘플기업");
        var withName = com.person.repit.interview.dto.request.FollowQuestionAiRequest.builder()
                .sessionId(r.getSessionId()).interviewId(1L).userId(2L).personaId(3L)
                .personality(r.getPersonality()).tone(r.getTone()).role(r.getRole()).level(r.getLevel())
                .questionId(4L).questionContent("샘플기업의 관점에서 왜 선택했나요?")
                .expectedAnswer("근거").answerContent("설명").companyName("샘플기업").companyBrief("요약").build();
        var h = harness(enabled(), index -> Mono.just(response("{\"required\":false,\"score\":4}")));
        h.client().decideFollowQuestion(withName).block();
        assertThat(h.bodies().getFirst().toString()).contains("샘플기업의 관점에서 왜 선택했나요?")
                .doesNotContain("<company_context>");
    }

    @Test
    void 추가질문이_불필요하면_기업_생성은_호출하지_않는다() {
        var h = harness(enabled(), index -> Mono.just(response("{\"required\":false,\"score\":5}")));
        var result = h.client().decideFollowQuestion(request("기업")).block();
        assertThat(result.getRequired()).isFalse();
        assertThat(result.getScore()).isEqualTo(5);
        assertThat(h.bodies()).hasSize(1);
    }

    @Test
    void 비활성_기본값은_기업_정보_없는_일반_질문을_유지한다() {
        var h = harness(CompanyFollowUpSettings.defaults(), index -> Mono.just(response(BASE)));
        assertThat(h.client().decideFollowQuestion(request("기업")).block().getContent()).isEqualTo("일반 질문?");
        assertThat(h.bodies()).hasSize(1);
        assertThat(h.bodies().getFirst().toString()).doesNotContain("<company_context>");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "-1", "6"})
    void 점수가_유효하지_않으면_기업_생성만_건너뛴다(String score) {
        String base = BASE.replace("\"score\":2", "\"score\":" + score);
        var h = harness(enabled(), index -> Mono.just(response(base)));
        var result = h.client().decideFollowQuestion(request("기업")).block();
        assertThat(result.getRequired()).isTrue();
        assertThat(result.getContent()).isEqualTo("일반 질문?");
        assertThat(h.bodies()).hasSize(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\n\t"})
    void 기업이_없으면_기능_활성_상태라도_한번만_호출한다(String name) {
        var h = harness(enabled(), index -> Mono.just(response(BASE)));
        assertThat(h.client().decideFollowQuestion(request(name)).block().getContent()).isEqualTo("일반 질문?");
        assertThat(h.bodies()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"required\":true}", "{\"required\":true,\"content\":\"질문\"}"})
    void 기본_판단이_불완전하면_기업_생성을_실행하지_않는다(String base) {
        var h = harness(enabled(), index -> Mono.just(response(base)));
        assertThat(h.client().decideFollowQuestion(request("기업")).block().getRequired()).isFalse();
        assertThat(h.bodies()).hasSize(1);
    }
}
