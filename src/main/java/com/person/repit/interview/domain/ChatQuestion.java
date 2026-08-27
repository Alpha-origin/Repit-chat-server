package com.person.repit.interview.domain;

import com.person.repit.interview.type.QuestionType;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

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
    private String expectedAnswer;
    private List<String> basedOn;
    private Long askedByPersonaId;
    private LocalDateTime createdAt;
}
