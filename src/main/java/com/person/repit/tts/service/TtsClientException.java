package com.person.repit.tts.service;

public class TtsClientException extends RuntimeException {
    private final boolean retryable;
    private final String errorCode;

    public TtsClientException(String message, boolean retryable) {
        this(message, retryable, null);
    }

    public TtsClientException(String message, boolean retryable, Throwable cause) {
        this(message, retryable, "TTS_GENERATION_FAILED", cause);
    }

    public TtsClientException(String message, boolean retryable, String errorCode, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
        this.errorCode = errorCode;
    }

    public boolean isRetryable() {
        return retryable;
    }

    public String getErrorCode() {
        return errorCode;
    }
}
