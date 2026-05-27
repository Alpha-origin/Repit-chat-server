package com.person.repit.interview.model;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatAnswer {
    private Long interviewId;
    private Long questionId;
    private Long userId;
    private Integer responseTime;
    private String content;
    private LocalDateTime createdAt;
}
