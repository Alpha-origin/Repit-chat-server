package com.person.repit.tts.service;

import com.person.repit.interview.domain.PersonaSnapshot;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.TtsProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PersonaTtsProfileResolver {
    private final TtsProperties properties;
    private final TtsPresetRegistry presetRegistry;

    public TtsProfile resolve(PersonaSnapshot persona) {
        int voiceIndex = persona.getVoiceIndex() == null
                ? properties.getDefaultVoiceIndex()
                : persona.getVoiceIndex();
        TtsPresetRegistry.ResolvedPreset preset = presetRegistry.resolve(
                persona.getTone(), persona.getType()
        );

        return TtsProfile.builder()
                .personaId(persona.getPersonaId())
                .voiceIndex(voiceIndex)
                .voiceId(properties.getVoices().get(voiceIndex))
                .modelId(properties.getElevenlabs().getModelId())
                .outputFormat(properties.getElevenlabs().getOutputFormat())
                .stability(preset.stability())
                .similarityBoost(properties.getDefaults().getSimilarityBoost())
                .style(preset.style())
                .speed(preset.speed())
                .useSpeakerBoost(properties.getDefaults().isUseSpeakerBoost())
                .pauseAfterSentenceMs(0)
                .profileVersion(properties.getProfileVersion())
                .build();
    }
}
