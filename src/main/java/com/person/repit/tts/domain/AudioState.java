package com.person.repit.tts.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class AudioState {
    private String sessionId;
    private Long questionId;
    private AudioStatus status;
    private String objectKey;
    private String url;
    private String errorCode;
    private int attempts;
    private String inputHash;
    private Instant createdAt;
    private Instant updatedAt;

    public boolean isTerminal() {
        return status == AudioStatus.READY || status == AudioStatus.FAILED;
    }
}
