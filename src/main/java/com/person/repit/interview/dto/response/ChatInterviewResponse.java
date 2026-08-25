package com.person.repit.interview.dto.response;

import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.InterviewPersona;
import com.person.repit.interview.type.InterviewStatus;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatInterviewResponse {
    private String sessionId;
    private Long interviewId;
    private Long userId;
    private List<InterviewPersona> interviewers;
    private InterviewStatus status;
    private int currentQuestionIndex;

    public static ChatInterviewResponse from(ChatInterviewSession interview) {
        return ChatInterviewResponse.builder()
                .sessionId(interview.getSessionId())
                .interviewId(interview.getInterviewId())
                .userId(interview.getUserId())
                .interviewers(interview.getInterviewers())
                .status(interview.getStatus())
                .currentQuestionIndex(interview.getCurrentQuestionIndex())
                .build();
    }
}
