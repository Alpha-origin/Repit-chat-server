package com.person.repit.interview.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.person.repit.interview.type.InterviewExpertise;
import com.person.repit.interview.type.InterviewLevel;
import com.person.repit.interview.type.InterviewMode;
import com.person.repit.interview.type.InterviewPersonality;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.InterviewTone;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
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
    private InterviewPersonality personality;

    @NotNull
    private InterviewTone tone;

    @NotNull
    private InterviewExpertise expertise;

    @NotNull
    private InterviewLevel level;

    @Valid
    @NotEmpty
    private List<Question> questions;

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

        private String expectedAnswer;

        private List<String> basedOn;

        @NotNull
        private Long personaId;
    }
}
