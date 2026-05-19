package com.person.repit.interview.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InterviewAnswerRequest {
    @NotNull
    private Long answerId;

    @NotNull
    private Long interviewId;

    @NotNull
    private Long questionId;

    @NotNull
    private Long userId;

    private Integer responseTime;

    private String content;
}
