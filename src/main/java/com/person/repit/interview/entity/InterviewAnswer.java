package com.person.repit.interview.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "interview_answer")
public class InterviewAnswer {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "answer_id", nullable = false)
    private Long answerId;

    @Column(name = "interview_id", nullable = false)
    private Long interviewId;

    private Long questionId;

    private Long user_id;

    private int response_time;

    private String content;

    private LocalDateTime created_at;
}
