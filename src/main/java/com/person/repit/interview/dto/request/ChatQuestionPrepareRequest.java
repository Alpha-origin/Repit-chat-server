package com.person.repit.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatQuestionPrepareRequest {
    @NotNull
    private Long questionId;

    @NotBlank
    private String intention;

    @NotBlank
    private String content;
}
