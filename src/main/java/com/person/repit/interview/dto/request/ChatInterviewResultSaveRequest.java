package com.person.repit.interview.dto.request;

import com.person.repit.interview.model.ChatAnswer;
import com.person.repit.interview.model.ChatInterviewSession;
import com.person.repit.interview.model.ChatQuestion;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.QuestionType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Optional;

@Getter
@Builder
public class ChatInterviewResultSaveRequest {

    @NotBlank
    private String sessionId;

    @NotNull
    private Long interviewId;

    @NotNull
    private Long userId;

    @NotNull
    private InterviewStatus status;

    @NotNull
    private List<Long> questions;

    @NotNull
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
