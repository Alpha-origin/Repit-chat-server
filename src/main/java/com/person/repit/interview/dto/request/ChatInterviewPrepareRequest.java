package com.person.repit.interview.dto.request;

import com.person.repit.interview.type.InterviewStyle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class ChatInterviewPrepareRequest {
    @NotBlank
    private String sessionId;

    @NotNull
    private Long interviewId;

    @NotNull
    private Long userId;

    @NotNull
    private Long personaId;

    @NotNull
    private InterviewStyle personaType;

    @Valid
    @NotEmpty
    private List<ChatQuestionPrepareRequest> questions;
}
