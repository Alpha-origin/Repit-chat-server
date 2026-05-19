package com.person.repit.interview.dto.response;

import com.person.repit.interview.entity.Interview;
import com.person.repit.interview.type.InterviewStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InterviewResponse {
    private Long interviewId;
    private Long userId;
    private Long personaId;
    private String sessionId;
    private InterviewStatus status;
    private LocalDateTime createdAt;

    public static InterviewResponse from(Interview interview) {
        return InterviewResponse.builder()
                .interviewId(interview.getInterviewId())
                .userId(interview.getUserId())
                .personaId(interview.getPersonaId())
                .sessionId(interview.getSessionId())
                .status(interview.getStatus())
                .createdAt(interview.getCreatedAt())
                .build();
    }
}
