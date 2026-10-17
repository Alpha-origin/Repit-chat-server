package com.person.repit.interview.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.*;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatAnswerRequest {

    @NotNull
    private Long questionId;

    @NotNull
    @PositiveOrZero
    private Integer responseTime;

    @NotBlank
    private String content;
}