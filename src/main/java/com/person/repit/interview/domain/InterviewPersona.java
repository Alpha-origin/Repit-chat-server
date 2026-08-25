package com.person.repit.interview.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InterviewPersona {
    private Long personaId;
    private String interviewerName;
    private String interviewerRole;
}
