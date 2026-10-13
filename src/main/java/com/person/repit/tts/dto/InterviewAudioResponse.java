package com.person.repit.tts.dto;

import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.domain.AudioStatus;

public record InterviewAudioResponse(
        Long questionId,
        AudioStatus status,
        String url,
        String errorCode
) {
    public static InterviewAudioResponse from(AudioState state) {
        return new InterviewAudioResponse(
                state.getQuestionId(), state.getStatus(), state.getUrl(), state.getErrorCode()
        );
    }

    public static InterviewAudioResponse pending(Long questionId) {
        return new InterviewAudioResponse(questionId, AudioStatus.PENDING, null, null);
    }
}
