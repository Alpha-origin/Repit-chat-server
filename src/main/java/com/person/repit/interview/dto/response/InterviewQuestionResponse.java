package com.person.repit.interview.dto.response;

import com.person.repit.interview.entity.InterviewQuestion;
import com.person.repit.interview.type.QuestionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InterviewQuestionResponse {
    private Long questionId;
    private Long interviewId;
    private Long parentId;
    private QuestionType type;
    private String intention;
    private String content;

    public static InterviewQuestionResponse from(InterviewQuestion question) {
        return InterviewQuestionResponse.builder()
                .questionId(question.getQuestionId())
                .interviewId(question.getInterviewId())
                .parentId(question.getParentId())
                .type(question.getType())
                .intention(question.getIntention())
                .content(question.getContent())
                .build();
    }
}
