package com.person.repit.tts.service;

import com.person.repit.interview.domain.PersonaSnapshot;
import com.person.repit.interview.type.InterviewMode;
import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.InterviewType;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.TtsProfile;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;

class TtsPresetRegistryTest {

    @ParameterizedTest
    @CsvSource({
            "SOLO,GENTLE,FRIENDLY,0.78,0.00,0.98",
            "SOLO,DIRECT,REALISTIC,0.51,0.30,1.12",
            "SOLO,PRESSURING,METICULOUS,0.01,1.00,1.19",
            "MULTI,GENTLE,FRIENDLY,0.76,0.00,0.98",
            "MULTI,GENTLE,REALISTIC,0.76,0.05,1.00",
            "MULTI,GENTLE,METICULOUS,0.80,0.03,0.99",
            "MULTI,DIRECT,FRIENDLY,0.38,0.42,1.13",
            "MULTI,DIRECT,REALISTIC,0.38,0.50,1.15",
            "MULTI,DIRECT,METICULOUS,0.42,0.48,1.14",
            "MULTI,PRESSURING,FRIENDLY,0.12,0.90,1.18",
            "MULTI,PRESSURING,REALISTIC,0.12,0.98,1.20",
            "MULTI,PRESSURING,METICULOUS,0.16,0.96,1.19"
    })
    void 명세의_프리셋_결과를_계산한다(
            InterviewMode mode,
            InterviewTone tone,
            InterviewType type,
            double stability,
            double style,
            double speed
    ) {
        TtsPresetRegistry registry = new TtsPresetRegistry(properties());

        TtsPresetRegistry.ResolvedPreset result = registry.resolve(mode, tone, type);

        assertThat(result.stability()).isEqualTo(stability);
        assertThat(result.style()).isEqualTo(style);
        assertThat(result.speed()).isEqualTo(speed);
    }

    @ParameterizedTest
    @CsvSource({"1,voice-one", "5,voice-five"})
    void voiceIndex로_실제_음성을_선택한다(int voiceIndex, String expected) {
        TtsProperties properties = properties();
        properties.getVoices().put(1, "voice-one");
        properties.getVoices().put(5, "voice-five");
        PersonaTtsProfileResolver resolver = new PersonaTtsProfileResolver(
                properties, new TtsPresetRegistry(properties)
        );
        PersonaSnapshot persona = PersonaSnapshot.builder()
                .personaId(1L)
                .voiceIndex(voiceIndex)
                .tone(InterviewTone.GENTLE)
                .type(InterviewType.FRIENDLY)
                .build();

        TtsProfile result = resolver.resolve(InterviewMode.MULTI, persona);

        assertThat(result.getVoiceId()).isEqualTo(expected);
    }

    private TtsProperties properties() {
        TtsProperties properties = new TtsProperties();
        properties.getPresets().getBase().put(
                InterviewMode.SOLO,
                tones(
                        preset(0.74, 0.04, 1.02),
                        preset(0.55, 0.18, 1.10),
                        preset(0.05, 1.00, 1.17)
                )
        );
        properties.getPresets().getBase().put(
                InterviewMode.MULTI,
                tones(
                        preset(0.72, 0.04, 1.02),
                        preset(0.42, 0.38, 1.13),
                        preset(0.20, 0.82, 1.17)
                )
        );
        properties.getPresets().getToneAdjust().put(
                InterviewTone.GENTLE, preset(0.04, -0.03, -0.02)
        );
        properties.getPresets().getToneAdjust().put(
                InterviewTone.DIRECT, preset(-0.04, 0.08, 0.02)
        );
        properties.getPresets().getToneAdjust().put(
                InterviewTone.PRESSURING, preset(-0.08, 0.12, 0.03)
        );
        properties.getPresets().getTypeAdjust().put(
                InterviewType.FRIENDLY, preset(0, -0.04, -0.02)
        );
        properties.getPresets().getTypeAdjust().put(
                InterviewType.REALISTIC, preset(0, 0.04, 0)
        );
        properties.getPresets().getTypeAdjust().put(
                InterviewType.METICULOUS, preset(0.04, 0.02, -0.01)
        );
        return properties;
    }

    private EnumMap<InterviewTone, TtsProperties.Preset> tones(
            TtsProperties.Preset gentle,
            TtsProperties.Preset direct,
            TtsProperties.Preset pressuring
    ) {
        EnumMap<InterviewTone, TtsProperties.Preset> result = new EnumMap<>(InterviewTone.class);
        result.put(InterviewTone.GENTLE, gentle);
        result.put(InterviewTone.DIRECT, direct);
        result.put(InterviewTone.PRESSURING, pressuring);
        return result;
    }

    private TtsProperties.Preset preset(double stability, double style, double speed) {
        TtsProperties.Preset result = new TtsProperties.Preset();
        result.setStability(stability);
        result.setStyle(style);
        result.setSpeed(speed);
        return result;
    }
}
