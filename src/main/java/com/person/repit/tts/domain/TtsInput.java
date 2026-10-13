package com.person.repit.tts.domain;

public record TtsInput(
        String text,
        String voiceId,
        String modelId,
        String outputFormat,
        VoiceSettings voiceSettings,
        String inputHash
) {
}
