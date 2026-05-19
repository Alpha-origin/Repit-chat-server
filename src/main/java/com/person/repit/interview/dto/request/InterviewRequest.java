package com.person.repit.interview.dto.request;

import com.person.repit.interview.type.InterviewStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class InterviewRequest {
    @NotNull
    private Long userId;

    @NotNull
    private Long personaId;

    @NotNull
    private Long sessionId;

    @NotBlank
    private InterviewStatus status;


}
