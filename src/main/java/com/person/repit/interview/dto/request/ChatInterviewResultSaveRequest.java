package com.person.repit.interview.dto.request;

import com.person.repit.interview.model.ChatAnswer;
import com.person.repit.interview.model.ChatInterviewSession;
import com.person.repit.interview.model.ChatQuestion;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.QuestionType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

@Getter
@Builder
public class ChatInterviewResultSaveRequest {

    private String sessionId;
    private Long interviewId;
    private Long userId;
    private InterviewStatus status;
    private List<Long> questions;
    private List<Long> answers;

    public static ChatInterviewResultSaveRequest from(ChatInterviewSession session) {
        return ChatInterviewResultSaveRequest.builder()
                .sessionId(session.getSessionId())
                .interviewId(session.getInterviewId())
                .userId(session.getUserId())
                .status(session.getStatus())
                .questions(Optional.ofNullable(session.getQuestions())
                        .orElse(List.of())
                        .stream()
                        .map(ChatQuestion::getQuestionId)
                        .toList()
                )
                .answers(Optional.ofNullable(session.getAnswers())
                        .orElse(List.of())
                        .stream()
                        .map(ChatAnswer::getAnswerId)
                        .toList()
                )
                .build();
    }
}
