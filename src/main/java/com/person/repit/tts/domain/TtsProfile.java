package com.person.repit.tts.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.*;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class TtsProfile {
    private Long personaId;
    private Integer voiceIndex;
    private String voiceId;
    private String modelId;
    private String outputFormat;
    private double stability;
    private double similarityBoost;
    private double style;
    private double speed;
    private boolean useSpeakerBoost;
    private int pauseAfterSentenceMs;
    private String profileVersion;
}
