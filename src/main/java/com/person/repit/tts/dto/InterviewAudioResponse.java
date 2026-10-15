package com.person.repit.tts.dto;

import com.person.repit.tts.domain.AudioState;

public record InterviewAudioResponse(
        Long questionId,
        AudioResponseStatus status,
        String url,
        String errorCode
) {
    public static InterviewAudioResponse from(AudioState state) {
        return new InterviewAudioResponse(
                state.getQuestionId(), AudioResponseStatus.from(state.getStatus(), state.getUrl()),
                state.getUrl(), state.getErrorCode()
        );
    }

    public static InterviewAudioResponse pending(Long questionId) {
        return new InterviewAudioResponse(questionId, AudioResponseStatus.PENDING, null, null);
    }
}
