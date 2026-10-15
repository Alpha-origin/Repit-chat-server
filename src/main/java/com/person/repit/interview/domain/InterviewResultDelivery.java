package com.person.repit.interview.domain;

import com.person.repit.interview.type.InterviewStatus;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
public class InterviewResultDelivery {
    public enum State { PENDING, SENT, BLOCKED }

    private String sessionId;
    private Long interviewId;
    private InterviewStatus interviewStatus;
    // Serialized once: retries must not regenerate question IDs or answer timestamps.
    private String payload;
    private State state = State.PENDING;
    private int attempts;
    private Instant firstFailureAt;
    private Instant nextAttemptAt;
    private String lastError;
}
