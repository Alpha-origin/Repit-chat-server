package com.person.repit.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InterviewAnswerRequest {

    @NotNull
    private Long interviewId;

    @NotNull
    private Long questionId;

    @NotNull
    private Long userId;

    @NotNull
    private Integer responseTime;

    @NotBlank
    private String content;
}
