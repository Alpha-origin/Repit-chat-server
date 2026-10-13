package com.person.repit.interview.dto.request;

import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.type.InterviewLevel;
import com.person.repit.interview.type.InterviewMajor;
import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.InterviewType;
import com.person.repit.interview.type.InterviewRole;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FollowQuestionAiRequestTest {

    @Test
    void 질문_카테고리와_의도를_따로_전달한다() {
        ChatQuestion question = ChatQuestion.builder()
                .questionId(1L)
                .category("tech_choice")
                .intention("분산락을 선택한 이유를 설명할 수 있는지")
                .build();

        FollowQuestionAiRequest request = FollowQuestionAiRequest.of(
                "session-id",
                1L,
                2L,
                3L,
                InterviewType.FRIENDLY,
                InterviewTone.GENTLE,
                InterviewMajor.BACKEND,
                InterviewLevel.NORMAL,
                InterviewRole.TECH,
                question,
                "답변",
                10
        );

        assertThat(request.getQuestionCategory()).isEqualTo("tech_choice");
        assertThat(request.getQuestionIntention())
                .isEqualTo("분산락을 선택한 이유를 설명할 수 있는지");
        assertThat(request.getRole()).isEqualTo(InterviewRole.TECH);
    }

    @Test
    void 기존_세션은_의도에_저장된_카테고리만_전달한다() {
        ChatQuestion question = ChatQuestion.builder()
                .questionId(1L)
                .intention("tech_choice")
                .build();

        FollowQuestionAiRequest request = FollowQuestionAiRequest.of(
                "session-id",
                1L,
                2L,
                3L,
                InterviewType.FRIENDLY,
                InterviewTone.GENTLE,
                InterviewMajor.BACKEND,
                InterviewLevel.NORMAL,
                InterviewRole.TECH,
                question,
                "답변",
                10
        );

        assertThat(request.getQuestionCategory()).isEqualTo("tech_choice");
        assertThat(request.getQuestionIntention()).isNull();
    }
}
