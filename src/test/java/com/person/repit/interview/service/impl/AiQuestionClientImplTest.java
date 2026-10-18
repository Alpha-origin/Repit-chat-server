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
        String prompt = ReflectionTestUtils.invokeMethod(client(), "createSystemPrompt");

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
