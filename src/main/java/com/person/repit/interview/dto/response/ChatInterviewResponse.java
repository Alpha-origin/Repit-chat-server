package com.person.repit.interview.dto.response;

import com.person.repit.interview.model.ChatInterviewSession;
import com.person.repit.interview.type.InterviewStatus;
import lombok.Builder;
import lombok.Getter;

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
                .sessionId(interview.getSessionId())
                .interviewId(interview.getInterviewId())
                .userId(interview.getUserId())
                .personaId(interview.getPersonaId())
                .status(interview.getStatus())
                .currentQuestionIndex(interview.getCurrentQuestionIndex())
                .build();
    }
}
