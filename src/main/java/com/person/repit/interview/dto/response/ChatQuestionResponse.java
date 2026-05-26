package com.person.repit.interview.dto.response;

import com.person.repit.interview.model.ChatQuestion;
import com.person.repit.interview.type.QuestionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatQuestionResponse {
    private Long questionId;
    private Long parentId;
    private QuestionType type;
    private String intention;
    private String content;

    public static ChatQuestionResponse from(ChatQuestion question) {
        return ChatQuestionResponse.builder()
                .questionId(question.getQuestionId())
                .parentId(question.getParentId())
                .type(question.getType())
                .intention(question.getIntention())
                .content(question.getContent())
                .build();
    }
}
