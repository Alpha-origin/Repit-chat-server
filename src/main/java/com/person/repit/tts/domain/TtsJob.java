package com.person.repit.tts.domain;

import lombok.Getter;
import reactor.core.publisher.Sinks;

@Getter
public class TtsJob implements Comparable<TtsJob> {
    private final String sessionId;
    private final Long questionId;
    private final int questionOrder;
    private final long sequence;
    private final TtsInput input;
    private final Sinks.One<AudioState> sink = Sinks.one();
    private volatile int priority;
    private volatile boolean cancelled;

    public TtsJob(
            String sessionId,
            Long questionId,
            int priority,
            int questionOrder,
            long sequence,
            TtsInput input
    ) {
        this.sessionId = sessionId;
        this.questionId = questionId;
        this.priority = priority;
        this.questionOrder = questionOrder;
        this.sequence = sequence;
        this.input = input;
    }

    public void promote() {
        priority = 0;
    }

    public void cancel() {
        cancelled = true;
    }

    @Override
    public int compareTo(TtsJob other) {
        int byPriority = Integer.compare(priority, other.priority);
        if (byPriority != 0) {
            return byPriority;
        }
        int byOrder = Integer.compare(questionOrder, other.questionOrder);
        return byOrder != 0 ? byOrder : Long.compare(sequence, other.sequence);
    }
}
