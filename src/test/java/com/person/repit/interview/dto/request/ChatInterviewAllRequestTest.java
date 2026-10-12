package com.person.repit.interview.dto.request;

import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatInterviewAllRequestTest {

    @Test
    void 새_세션은_카테고리와_의도를_따로_전송한다() {
        ChatQuestion question = ChatQuestion.builder()
                .questionId(1L)
                .category("tech_choice")
                .intention("분산락을 선택한 이유를 설명할 수 있는지")
                .build();

        ChatInterviewAllRequest.QuestionRequest result = questionRequest(question);

        assertThat(result.getCategory()).isEqualTo("tech_choice");
        assertThat(result.getIntention()).isEqualTo("분산락을 선택한 이유를 설명할 수 있는지");
    }

    @Test
    void 기존_세션은_의도에_저장된_카테고리만_전송한다() {
        ChatQuestion question = ChatQuestion.builder()
                .questionId(1L)
                .intention("tech_choice")
                .build();

        ChatInterviewAllRequest.QuestionRequest result = questionRequest(question);

        assertThat(result.getCategory()).isEqualTo("tech_choice");
        assertThat(result.getIntention()).isNull();
    }

    private ChatInterviewAllRequest.QuestionRequest questionRequest(ChatQuestion question) {
        ChatInterviewSession session = ChatInterviewSession.builder()
                .questions(List.of(question))
                .answers(new ArrayList<>())
                .build();

        return ChatInterviewAllRequest.from(session)
                .getQnaRequests()
                .getFirst()
                .getQuestion();
    }
}
