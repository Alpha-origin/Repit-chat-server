package com.person.repit.interview.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.service.AiRequestConcurrencyLimiter;
import com.person.repit.interview.type.InterviewLevel;
import com.person.repit.interview.type.InterviewMajor;
import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.InterviewType;
import com.person.repit.interview.type.InterviewRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.reactive.function.client.WebClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class AiQuestionClientImplTest {

    @Test
    void 모델이_거절하는_temperature_옵션을_보내지_않는다() {
        var request = jobRequest(InterviewMajor.QA_ENGINEER, InterviewRole.TECH);
        Map<?, ?> body = ReflectionTestUtils.invokeMethod(client(), "createRequestBody", request);
        assertThat(body.containsKey("temperature")).isFalse();
        assertThat(body.get("messages")).isNotNull();
    }


    @Test
    void 사용자_프롬프트에_카테고리와_없는_의도를_표시한다() throws Exception {
        FollowQuestionAiRequest request = FollowQuestionAiRequest.builder()
                .sessionId("session-id")
                .interviewId(1L)
                .userId(2L)
                .personaId(3L)
                .personality(InterviewType.FRIENDLY)
                .tone(InterviewTone.GENTLE)
                .major(InterviewMajor.BACKEND)
                .level(InterviewLevel.NORMAL)
                .role(InterviewRole.TECH)
                .questionId(4L)
                .questionCategory("tech_choice")
                .questionIntention(null)
                .questionContent("질문")
                .expectedAnswer("참고 답안")
                .answerContent("답변")
                .responseTime(10)
                .build();

        String prompt = ReflectionTestUtils.invokeMethod(client(), "createUserPrompt", request);

        assertThat(prompt)
                .contains("questionCategory: tech_choice")
                .contains("questionIntention: 없음")
                .contains("role: TECH");
        assertThat(prompt).isEqualTo(originalPrompt("user"));
    }

    @Test
    void 시스템_프롬프트가_꼬리질문_의도를_새로_작성하도록_지시한다() throws Exception {
        String prompt = ReflectionTestUtils.invokeMethod(client(), "createSystemPrompt", InterviewMajor.BACKEND);

        assertThat(prompt)
                .contains("category는 질문 분류 정보로만 참고합니다")
                .contains("꼬리질문이 확인하려는 것을 '~을 설명할 수 있는지' 형식의 한 문장으로 작성합니다")
                .contains("카테고리 이름을 쓰지 않습니다");
        assertThat(prompt).contains("spokenText").contains("[발음 문장 작성]");
        assertThat(prompt).isEqualTo(originalPrompt("system"));
    }

    @Test
    void 비기술_면접관은_major가_없어도_프롬프트를_생성한다() {
        FollowQuestionAiRequest request = FollowQuestionAiRequest.builder()
                .sessionId("session-id")
                .interviewId(1L)
                .userId(2L)
                .personaId(3L)
                .personality(InterviewType.FRIENDLY)
                .tone(InterviewTone.GENTLE)
                .major(null)
                .level(InterviewLevel.NORMAL)
                .role(InterviewRole.HR)
                .questionId(4L)
                .questionContent("질문")
                .expectedAnswer("참고 답안")
                .answerContent("답변")
                .responseTime(10)
                .build();

        String prompt = ReflectionTestUtils.invokeMethod(client(), "createUserPrompt", request);

        assertThat(prompt)
                .contains("major: 없음")
                .contains("role: HR")
                .contains("협업 방식을 확인하세요");
    }

    @ParameterizedTest
    @ValueSource(strings = {"샘플커머스", "R&D 센터", "AT&T", "H&M", "  샘플\r\n\t 커머스  "})
    void 기업이_있어도_기본_판단의_요청은_기업_없음과_동일하다(String name) throws Exception {
        Map<?, ?> withCompany = ReflectionTestUtils.invokeMethod(client(), "createRequestBody", companyRequest(name));
        Map<?, ?> withoutCompany = ReflectionTestUtils.invokeMethod(client(), "createRequestBody", companyRequest(null));
        assertThat(withCompany).isEqualTo(withoutCompany);
        assertThat(withCompany.get("system")).isEqualTo(originalPrompt("system"));
        String user = ReflectionTestUtils.invokeMethod(client(), "createUserPrompt", companyRequest(name));
        assertThat(user).isEqualTo(originalPrompt("user")).doesNotContain("[기업 컨텍스트]");
    }

    @Test
    void 비개발_QA_기준_원문을_유지한다() throws Exception {
        FollowQuestionAiRequest request = jobRequest(InterviewMajor.QA_ENGINEER, InterviewRole.TECH);
        assertThat((String) ReflectionTestUtils.invokeMethod(client(), "createSystemPrompt", request.getMajor()))
                .isEqualTo(jobPrompt("nondev-qa-system"));
        assertThat((String) ReflectionTestUtils.invokeMethod(client(), "createUserPrompt", request))
                .isEqualTo(jobPrompt("nondev-qa-user"));
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.CsvSource({
            "FRONTEND,프론트엔드 기술 면접관의 관점에서 질문하세요.",
            "BACKEND,백엔드 기술 면접관의 관점에서 질문하세요.",
            "WEB_PUBLISHER,웹 퍼블리셔 기술 면접관의 관점에서 질문하세요.",
            "DATA_ANALYST,데이터 분석 전문 면접관의 관점에서 문제 정의",
            "DBA_INFRA,DBA·인프라 전문 면접관의 관점에서 설계",
            "QA_ENGINEER,QA 전문 면접관의 관점에서 테스트 설계",
            "SECURITY,공격 코드를 새로 작성하게 하지 마세요.",
            "PRODUCT_MANAGER,서비스 기획 전문 면접관의 관점에서 문제 정의",
            "IT_CONSULTANT,IT 컨설팅 전문 면접관의 관점에서 고객 요구 분석"
    })
    void IT_직무별_관점과_역할을_선택한다(InterviewMajor major, String instruction) {
        FollowQuestionAiRequest request = jobRequest(major, InterviewRole.TECH);
        String system = ReflectionTestUtils.invokeMethod(client(), "createSystemPrompt", major);
        String user = ReflectionTestUtils.invokeMethod(client(), "createUserPrompt", request);
        assertThat(system).startsWith("당신은 " + (major.isDevelopment() ? "개발자 기술" : major.label() + " 직무") + " 면접에서");
        assertThat(user).contains(instruction).contains(major.isDevelopment()
                ? "기술적 근거와 트레이드오프를 확인하세요."
                : "직무 판단의 근거와 대안, 본인이 한 일을 확인하세요.");
    }

    @ParameterizedTest
    @org.junit.jupiter.params.provider.EnumSource(value = InterviewRole.class, names = {"HR", "CEO"})
    void 비개발_면접은_전공_없는_면접관_차례에도_세션_직무로_시스템_프롬프트를_정한다(InterviewRole role) {
        FollowQuestionAiRequest request = sessionRequest(InterviewMajor.QA_ENGINEER, null, role);
        Map<?, ?> body = ReflectionTestUtils.invokeMethod(client(), "createRequestBody", request);
        assertThat((String) body.get("system")).startsWith("당신은 QA 엔지니어 직무 면접에서");
    }

    @Test
    void 개발_면접의_HR_차례는_개발자_기술_면접을_유지한다() throws Exception {
        FollowQuestionAiRequest request = sessionRequest(InterviewMajor.BACKEND, null, InterviewRole.HR);
        Map<?, ?> body = ReflectionTestUtils.invokeMethod(client(), "createRequestBody", request);
        assertThat(body.get("system")).isEqualTo(originalPrompt("system"));
    }

    @Test
    void 세션_직무가_없으면_면접관_전공으로_시스템_프롬프트를_정한다() throws Exception {
        FollowQuestionAiRequest request = sessionRequest(null, InterviewMajor.QA_ENGINEER, InterviewRole.TECH);
        Map<?, ?> body = ReflectionTestUtils.invokeMethod(client(), "createRequestBody", request);
        assertThat(body.get("system")).isEqualTo(jobPrompt("nondev-qa-system"));
    }

    private FollowQuestionAiRequest sessionRequest(InterviewMajor session, InterviewMajor persona, InterviewRole role) {
        return FollowQuestionAiRequest.builder().sessionId("session-id")
                .interviewId(1L).userId(2L).personaId(3L)
                .personality(InterviewType.FRIENDLY).tone(InterviewTone.GENTLE)
                .major(persona).interviewMajor(session).level(InterviewLevel.NORMAL).role(role)
                .questionId(4L).questionCategory("tech_choice").questionContent("질문")
                .expectedAnswer("참고 답안").answerContent("답변").responseTime(10).build();
    }

    private FollowQuestionAiRequest jobRequest(InterviewMajor major, InterviewRole role) {
        return FollowQuestionAiRequest.builder().sessionId("session-id")
                .interviewId(1L).userId(2L).personaId(3L)
                .personality(InterviewType.FRIENDLY).tone(InterviewTone.GENTLE)
                .major(major).level(InterviewLevel.NORMAL).role(role)
                .questionId(4L).questionCategory("tech_choice").questionContent("질문")
                .expectedAnswer("참고 답안").answerContent("답변").responseTime(10).build();
    }

    private String jobPrompt(String name) throws IOException {
        try (var input = getClass().getResourceAsStream("/prompts/follow-question/" + name + ".txt")) {
            return new String(java.util.Objects.requireNonNull(input).readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private FollowQuestionAiRequest companyRequest(String name) {
        return FollowQuestionAiRequest.builder().sessionId("session-id")
                .interviewId(1L).userId(2L).personaId(3L)
                .personality(InterviewType.FRIENDLY).tone(InterviewTone.GENTLE)
                .major(InterviewMajor.BACKEND).level(InterviewLevel.NORMAL).role(InterviewRole.TECH)
                .questionId(4L).questionCategory("tech_choice").questionContent("질문")
                .expectedAnswer("참고 답안").answerContent("답변").responseTime(10)
                .companyName(name).companyBrief("주문 동시성과 오너십").build();
    }

    private String originalPrompt(String type) throws IOException {
        // 기업 기능 추가 전 커밋 5a49f12의 원문이며 공백과 줄바꿈도 비교한다.
        String path = "/prompts/follow-question/no-company-" + type + ".txt";
        try (var input = getClass().getResourceAsStream(path)) {
            if (input == null) {
                throw new IOException("기존 프롬프트 리소스 누락: " + path);
            }
            return new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private AiQuestionClientImpl client() {
        return new AiQuestionClientImpl(
                new ObjectMapper(),
                "model",
                800,
                mock(WebClient.class),
                mock(RepitMetrics.class),
                mock(AiRequestConcurrencyLimiter.class)
        );
    }
}
