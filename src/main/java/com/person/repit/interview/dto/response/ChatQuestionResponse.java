package com.person.repit.interview.dto.response;

import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.type.QuestionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatQuestionResponse {
    private Long questionId;
    private Long parentId;
    private Long followQuestionId;
    private QuestionType type;
    private String intention;
    private String content;
    private Long personaId;

    public static ChatQuestionResponse from(ChatQuestion question) {
        return ChatQuestionResponse.builder()
                .questionId(question.getQuestionId())
                .parentId(question.getParentId())
                .followQuestionId(question.getFollowQuestionId())
                .type(question.getType())
                .intention(question.getIntention())
                .content(question.getContent())
                .personaId(question.getAskedByPersonaId())
                .build();
    }
}
