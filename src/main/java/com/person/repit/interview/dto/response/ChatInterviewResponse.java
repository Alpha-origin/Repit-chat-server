package com.person.repit.interview.dto.response;

import com.person.repit.interview.entity.Interview;
import com.person.repit.interview.model.ChatInterviewSession;
import com.person.repit.interview.type.InterviewStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ChatInterviewResponse {
    private String sessionId;
    private Long interviewId;
    private Long userId;
    private Long personaId;
    private InterviewStatus status;
    private int currentQuestionIndex;

    public static ChatInterviewResponse from(ChatInterviewSession interview) {
        return ChatInterviewResponse.builder()
                .interviewId(interview.getInterviewId())
                .userId(interview.getUserId())
                .personaId(interview.getPersonaId())
                .sessionId(interview.getSessionId())
                .status(interview.getStatus())
                .build();
    }
}
