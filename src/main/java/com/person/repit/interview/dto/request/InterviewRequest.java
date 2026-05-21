package com.person.repit.interview.dto.request;

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


}
