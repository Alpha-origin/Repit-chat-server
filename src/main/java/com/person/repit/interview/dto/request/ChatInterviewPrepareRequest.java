package com.person.repit.interview.dto.request;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.person.repit.interview.type.InterviewStatus;
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
