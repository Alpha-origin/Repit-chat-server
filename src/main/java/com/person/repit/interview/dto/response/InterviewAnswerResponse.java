package com.person.repit.interview.dto.response;

import com.person.repit.interview.entity.InterviewAnswer;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InterviewAnswerResponse {
    private Long answerId;
    private Long interviewId;
    private Long questionId;
    private Long userId;
    private Integer responseTime;
    private String content;
    private LocalDateTime createdAt;

    public static InterviewAnswerResponse from(InterviewAnswer answer) {
        return InterviewAnswerResponse.builder()
                .answerId(answer.getAnswerId())
                .interviewId(answer.getInterviewId())
                .questionId(answer.getQuestionId())
                .userId(answer.getUserId())
                .responseTime(answer.getResponseTime())
                .content(answer.getContent())
                .createdAt(answer.getCreatedAt())
                .build();
    }
}
