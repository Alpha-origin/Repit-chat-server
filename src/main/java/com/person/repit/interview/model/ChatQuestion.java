package com.person.repit.interview.model;

import com.person.repit.interview.type.QuestionType;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatQuestion {
    private Long questionId;
    private Long parentId;
    private QuestionType type;
    private String intention;
    private String content;
    private LocalDateTime createdAt;
}
