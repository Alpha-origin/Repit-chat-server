package com.person.repit.tts.dto;

import com.person.repit.tts.domain.AudioStatus;
import org.springframework.util.StringUtils;

public enum AudioResponseStatus {
    READY,
    PENDING,
    FAILED;

    public static AudioResponseStatus from(AudioStatus status, String url) {
        return switch (status) {
            case QUEUED, GENERATING, PENDING -> PENDING;
            case READY -> StringUtils.hasText(url) ? READY : PENDING;
            case FAILED -> FAILED;
        };
    }
}
