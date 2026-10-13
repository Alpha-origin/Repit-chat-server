package com.person.repit.tts.service;

public class TtsClientException extends RuntimeException {
    private final boolean retryable;

    public TtsClientException(String message, boolean retryable) {
        super(message);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
