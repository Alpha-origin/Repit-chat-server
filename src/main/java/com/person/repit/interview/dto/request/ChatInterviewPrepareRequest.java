package com.person.repit.interview.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.person.repit.interview.type.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatInterviewPrepareRequest {

    @NotBlank
    private String sessionId;

    @NotNull
    private Long interviewId;

    @NotNull
    private Long userId;

    @NotNull
    private InterviewStatus status;

    @NotNull
    private InterviewMode mode;

    @NotNull
    private InterviewType personality;

    @NotNull
    private InterviewTone tone;

    @NotNull
    private InterviewMajor major;

    @NotNull
    private InterviewLevel level;

    @Valid
    private Company company;

    @Valid
    private List<@NotNull Persona> personas;

    @Valid
    @NotEmpty
    private List<@NotNull Question> questions;

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Company {

        @NotBlank
        @Size(max = 30)
        private String displayName;

        @NotBlank
        @Size(max = 600)
        private String interviewerBrief;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Question {

        @NotNull
        private Long id;

        private String category;

        @NotBlank
        private String question;

        private String intention;

        private String spokenText;

        private String expectedAnswer;

        private List<String> basedOn;

        @NotNull
        private Long personaId;
    }

    @Getter
    @Setter
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Persona {

        @NotNull
        private Long personaId;

        private String personaName;

        @NotNull
        private InterviewRole role;

        private InterviewMajor major;

        @NotNull
        private InterviewType type;

        @NotNull
        private InterviewTone tone;

        @NotNull
        private InterviewLevel level;

        private Integer career;

        private InterviewGender gender;

        @Min(1)
        @Max(5)
        private Integer voiceIndex;
    }
}
