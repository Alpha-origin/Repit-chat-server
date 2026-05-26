package com.person.repit.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ChatAnswerRequest {

    @NotNull
    private Long questionId;

    private Integer responseTime;

    @NotBlank
    private String content;
}
