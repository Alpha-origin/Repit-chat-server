package com.person.repit.interview.entity;

import com.person.repit.interview.type.InterviewStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "interview")
public class Interview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "interview_id", nullable = false)
    private Long interviewId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "persona_id", nullable = false)
    private Long personaId;

    @Column(name = "session_id", nullable = false, length = 100)
    private String sessionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    @Builder.Default
    private InterviewStatus status = InterviewStatus.IN_PROGRESS;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
