package com.person.repit.interview.dto.request;

import com.person.repit.interview.type.QuestionType;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InterviewQuestionRequest {

    @NotNull
    private Long interviewId;

    private Long parentId;

    @NotNull
    private QuestionType type;

    @NotBlank
    private String intention;

    @NotBlank
    private String content;
}
