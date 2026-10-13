package com.person.repit.tts.domain;

import com.fasterxml.jackson.annotation.JsonProperty;

public record VoiceSettings(
        double stability,
        @JsonProperty("similarity_boost") double similarityBoost,
        double style,
        double speed,
        @JsonProperty("use_speaker_boost") boolean useSpeakerBoost
) {
}
