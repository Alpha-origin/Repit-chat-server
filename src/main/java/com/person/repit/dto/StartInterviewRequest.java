package com.person.repit.dto;

import com.person.repit.type.InterviewStyle;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class StartInterviewRequest {
    private Long personaId;
    private InterviewStyle style;
}
