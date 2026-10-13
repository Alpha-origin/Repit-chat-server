package com.person.repit.tts.service;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class TtsQueueMetrics {

    public TtsQueueMetrics(MeterRegistry registry, InterviewTtsScheduler scheduler) {
        for (int priority = 0; priority <= 2; priority++) {
            int value = priority;
            Gauge.builder("tts.job.queue.size", scheduler,
                            target -> target.queuedCount(value))
                    .tag("priority", String.valueOf(priority))
                    .description("우선순위별 TTS 대기 작업 수")
                    .register(registry);
        }
        Gauge.builder("tts.job.running", scheduler, InterviewTtsScheduler::runningCount)
                .description("실행 중인 TTS 작업 수")
                .register(registry);
    }
}
