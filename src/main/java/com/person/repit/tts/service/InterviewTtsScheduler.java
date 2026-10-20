package com.person.repit.tts.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewTtsScheduler {
    private final TtsProperties properties;
    private final TtsInputBuilder inputBuilder;
    private final TtsClient ttsClient;
    private final AudioStorage audioStorage;
    private final AudioStateRepository stateRepository;
    private final RepitMetrics metrics;

    private final Map<JobKey, TtsJob> jobs = new ConcurrentHashMap<>();
    private final PriorityQueue<TtsJob> waiting = new PriorityQueue<>();
    private final Set<TtsJob> running = ConcurrentHashMap.newKeySet();
    private final Map<String, Instant> closed = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong();

    public Mono<AudioState> register(
            String sessionId,
            ChatQuestion question,
            TtsProfile profile,
            int priority,
            int questionOrder
    ) {
        if (!properties.isEnabled()) {
            return Mono.just(failedState(sessionId, question.getQuestionId(), "TTS_DISABLED"));
        }
        if (profile == null || profile.getVoiceId() == null || profile.getVoiceId().isBlank()) {
            AudioState failed = failedState(
                    sessionId, question.getQuestionId(), "TTS_VOICE_NOT_CONFIGURED"
            );
            return stateRepository.save(failed).thenReturn(failed);
        }

        JobKey key = new JobKey(sessionId, question.getQuestionId());
        TtsJob existingJob = jobs.get(key);
        if (existingJob != null) {
            return stateRepository.get(sessionId, question.getQuestionId());
        }
        TtsInput input = inputBuilder.build(question, profile);
        TtsJob job = new TtsJob(
                sessionId, question.getQuestionId(), priority, questionOrder,
                sequence.incrementAndGet(), input
        );
        AudioState state = queuedState(job);
        return stateRepository.create(state)
                .flatMap(created -> stateRepository.get(sessionId, question.getQuestionId())
                        .defaultIfEmpty(state)
                        .doOnNext(current -> {
                            if (Boolean.TRUE.equals(created) || !current.isTerminal()) {
                                enqueue(key, job);
                            }
                        }));
    }

    public synchronized void changePriority(String sessionId, Long questionId, int priority) {
        TtsJob job = jobs.get(new JobKey(sessionId, questionId));
        if (job != null && waiting.remove(job)) {
            job.changePriority(priority);
            waiting.add(job);
            drain();
        }
    }

    public Mono<AudioState> await(String sessionId, Long questionId) {
        TtsJob job = jobs.get(new JobKey(sessionId, questionId));
        return job == null ? stateRepository.get(sessionId, questionId) : job.getSink().asMono();
    }

    public synchronized void cancelSession(String sessionId) {
        closed.put(sessionId, Instant.now().plusSeconds(600));
        waiting.removeIf(job -> {
            if (sessionId.equals(job.getSessionId())) {
                job.cancel();
                jobs.remove(new JobKey(job.getSessionId(), job.getQuestionId()));
                job.getSink().tryEmitEmpty();
                return true;
            }
            return false;
        });
        running.stream()
                .filter(job -> sessionId.equals(job.getSessionId()))
                .forEach(TtsJob::cancel);
        cleanupClosed();
    }

    public boolean hasJob(String sessionId, Long questionId) {
        return jobs.containsKey(new JobKey(sessionId, questionId));
    }

    public synchronized int queuedCount(int priority) {
        return (int) waiting.stream().filter(job -> job.getPriority() == priority).count();
    }

    public int runningCount() {
        return running.size();
    }

    private synchronized void enqueue(JobKey key, TtsJob job) {
        if (closed.containsKey(job.getSessionId()) || jobs.putIfAbsent(key, job) != null) {
            return;
        }
        waiting.add(job);
        drain();
    }

    private synchronized void drain() {
        while (running.size() < properties.getQueue().getGlobalMaxConcurrency()) {
            TtsJob candidate = findRunnable();
            if (candidate == null) {
                return;
            }
            waiting.remove(candidate);
            running.add(candidate);
            start(candidate);
        }
    }

    private TtsJob findRunnable() {
        return waiting.stream().sorted().filter(this::canRun).findFirst().orElse(null);
    }

    private boolean canRun(TtsJob candidate) {
        long sessionRunning = running.stream()
                .filter(job -> job.getSessionId().equals(candidate.getSessionId())).count();
        if (sessionRunning >= properties.getQueue().getPerSessionMax()) {
            return false;
        }
        if (candidate.getPriority() == 0) {
            return true;
        }
        long prefetchRunning = running.stream().filter(job -> job.getPriority() > 0).count();
        long sessionPrefetch = running.stream()
                .filter(job -> job.getPriority() > 0)
                .filter(job -> job.getSessionId().equals(candidate.getSessionId())).count();
        return prefetchRunning < properties.getQueue().getPrefetchMaxConcurrency()
                && sessionPrefetch < properties.getQueue().getPerSessionPrefetchMax();
    }

    private void start(TtsJob job) {
        AudioState generating = queuedState(job);
        generating.setStatus(AudioStatus.GENERATING);
        generating.setAttempts(1);
        generating.setUpdatedAt(Instant.now());

        stateRepository.save(generating)
                .then(synthesize(job))
                .flatMap(bytes -> upload(job, bytes))
                .flatMap(stored -> completeReady(job, generating, stored))
                .onErrorResume(error -> completeFailed(job, generating, error))
                .doFinally(ignored -> finish(job))
                .subscribe();
    }

    private Mono<byte[]> synthesize(TtsJob job) {
        Retry retry = Retry.backoff(
                        properties.getRetry().getMaxRetries(),
                        properties.getRetry().getBackoffs().getFirst()
                )
                .maxBackoff(properties.getRetry().getBackoffs().getLast())
                .filter(this::isRetryable)
                .doBeforeRetry(ignored -> metrics.ttsSynthesisRequest("retry"));
        return metrics.recordTtsSynthesis(
                ttsClient.synthesize(job.getInput()).retryWhen(retry), "request"
        ).doOnSuccess(ignored -> metrics.ttsSynthesisRequest("success"))
                .doOnError(ignored -> metrics.ttsSynthesisRequest("failed"));
    }

    private Mono<StoredAudio> upload(TtsJob job, byte[] bytes) {
        Retry retry = Retry.max(properties.getRetry().getUploadRetries());
        return audioStorage.put(
                        job.getSessionId(), job.getQuestionId(), job.getInput().inputHash(), bytes
                )
                .retryWhen(retry)
                .onErrorMap(AudioStorageException::new);
    }

    private Mono<Void> completeReady(TtsJob job, AudioState state, StoredAudio stored) {
        if (job.isCancelled() || isClosed(job.getSessionId())) {
            return Mono.empty();
        }
        state.setStatus(AudioStatus.READY);
        state.setObjectKey(stored.objectKey());
        state.setUrl(stored.url());
        state.setUpdatedAt(Instant.now());
        return stateRepository.save(state).doOnSuccess(ignored -> job.getSink().tryEmitValue(state));
    }

    private Mono<Void> completeFailed(TtsJob job, AudioState state, Throwable error) {
        if (job.isCancelled() || isClosed(job.getSessionId())) {
            return Mono.empty();
        }
        state.setStatus(AudioStatus.FAILED);
        state.setErrorCode(error instanceof AudioStorageException
                ? "AUDIO_STORAGE_FAILED"
                : error instanceof TtsClientException clientError
                        ? clientError.getErrorCode() : "TTS_GENERATION_FAILED");
        state.setUpdatedAt(Instant.now());
        log.warn("TTS 작업 실패 sessionId={}, questionId={}",
                job.getSessionId(), job.getQuestionId(), error);
        return stateRepository.save(state).doOnSuccess(ignored -> job.getSink().tryEmitValue(state));
    }

    private synchronized void finish(TtsJob job) {
        running.remove(job);
        jobs.remove(new JobKey(job.getSessionId(), job.getQuestionId()));
        drain();
    }

    private boolean isRetryable(Throwable error) {
        return !(error instanceof TtsClientException clientError) || clientError.isRetryable();
    }

    private boolean isClosed(String sessionId) {
        Instant until = closed.get(sessionId);
        return until != null && until.isAfter(Instant.now());
    }

    private void cleanupClosed() {
        Instant now = Instant.now();
        closed.entrySet().removeIf(entry -> entry.getValue().isBefore(now));
    }

    private AudioState queuedState(TtsJob job) {
        Instant now = Instant.now();
        return AudioState.builder().sessionId(job.getSessionId()).questionId(job.getQuestionId())
                .status(AudioStatus.QUEUED).attempts(0).inputHash(job.getInput().inputHash())
                .createdAt(now).updatedAt(now).build();
    }

    private AudioState failedState(String sessionId, Long questionId, String errorCode) {
        Instant now = Instant.now();
        return AudioState.builder().sessionId(sessionId).questionId(questionId)
                .status(AudioStatus.FAILED).errorCode(errorCode)
                .createdAt(now).updatedAt(now).build();
    }

    private record JobKey(String sessionId, Long questionId) {
    }

}
