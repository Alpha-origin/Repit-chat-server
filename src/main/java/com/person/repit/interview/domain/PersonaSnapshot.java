package com.person.repit.interview.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.person.repit.interview.type.*;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PersonaSnapshot {
    private Long personaId;
    private String name;
    private InterviewRole role;
    private InterviewMajor major;
    private InterviewType type;
    private InterviewTone tone;
    private InterviewLevel level;
    private Integer career;
    private InterviewGender gender;
    private Integer voiceIndex;
}
