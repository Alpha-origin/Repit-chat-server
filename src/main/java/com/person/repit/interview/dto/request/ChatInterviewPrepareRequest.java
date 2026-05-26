package com.person.repit.interview.dto.request;

import com.person.repit.interview.type.InterviewStyle;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

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

    @Valid
    @NotEmpty
    private InterviewStyle style;

    @Getter
    @NoArgsConstructor
    public static class QuestionRequest {
        @NotNull
        private Long questionId;

        @NotBlank
        private String intention;

        @NotBlank
        private String content;
    }
}
