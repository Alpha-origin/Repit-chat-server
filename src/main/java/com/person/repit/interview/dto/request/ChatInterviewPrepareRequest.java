package com.person.repit.interview.dto.request;

import com.person.repit.interview.type.InterviewLevel;
import com.person.repit.interview.type.InterviewStyle;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
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

    @NotNull
    private InterviewLevel level;

    @NotNull
    private UUID jobId;
}
