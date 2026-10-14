package com.person.repit.tts.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.domain.PersonaSnapshot;
import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.InterviewType;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.TtsInput;
import com.person.repit.tts.domain.TtsProfile;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.EnumMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TtsPresetRegistryTest {

    @ParameterizedTest
    @CsvSource({
            "GENTLE,FRIENDLY,0.78,0.00,0.98",
            "GENTLE,REALISTIC,0.76,0.05,1.00",
            "GENTLE,METICULOUS,0.80,0.03,0.99",
            "DIRECT,FRIENDLY,0.38,0.42,1.13",
            "DIRECT,REALISTIC,0.51,0.30,1.12",
            "DIRECT,METICULOUS,0.42,0.48,1.14",
            "PRESSURING,FRIENDLY,0.12,0.90,1.18",
            "PRESSURING,REALISTIC,0.12,0.98,1.20",
            "PRESSURING,METICULOUS,0.01,1.00,1.19"
    })
    void 설정된_9칸_프리셋을_그대로_조회한다(
            InterviewTone tone,
            InterviewType type,
            double stability,
            double style,
            double speed
    ) {
        TtsPresetRegistry registry = new TtsPresetRegistry(properties());

        TtsPresetRegistry.ResolvedPreset result = registry.resolve(tone, type);

        assertThat(result.stability()).isEqualTo(stability);
        assertThat(result.style()).isEqualTo(style);
        assertThat(result.speed()).isEqualTo(speed);
    }

    @ParameterizedTest
    @CsvSource({
            "GENTLE,FRIENDLY,0.78,0.00,0.98",
            "GENTLE,REALISTIC,0.76,0.05,1.00",
            "GENTLE,METICULOUS,0.80,0.03,0.99",
            "DIRECT,FRIENDLY,0.38,0.42,1.13",
            "DIRECT,REALISTIC,0.51,0.30,1.12",
            "DIRECT,METICULOUS,0.42,0.48,1.14",
            "PRESSURING,FRIENDLY,0.12,0.90,1.18",
            "PRESSURING,REALISTIC,0.12,0.98,1.20",
            "PRESSURING,METICULOUS,0.01,1.00,1.19"
    })
    void 요청_voice_settings에_9칸_프리셋을_전달한다(
            InterviewTone tone,
            InterviewType type,
            double stability,
            double style,
            double speed
    ) {
        TtsProperties properties = properties();
        PersonaTtsProfileResolver resolver = new PersonaTtsProfileResolver(
                properties, new TtsPresetRegistry(properties)
        );
        TtsProfile profile = resolver.resolve(persona(1, tone, type));
        TtsInput input = inputBuilder(properties).build(question(), profile);

        assertThat(input.voiceSettings().stability()).isEqualTo(stability);
        assertThat(input.voiceSettings().style()).isEqualTo(style);
        assertThat(input.voiceSettings().speed()).isEqualTo(speed);
        assertThat(input.voiceSettings().similarityBoost()).isEqualTo(0.8);
        assertThat(input.voiceSettings().useSpeakerBoost()).isTrue();
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

        TtsProfile result = resolver.resolve(
                persona(voiceIndex, InterviewTone.GENTLE, InterviewType.FRIENDLY)
        );

        assertThat(result.getVoiceId()).isEqualTo(expected);
    }

    @Test
    void 프리셋_칸이_누락되면_기동에_실패한다() {
        TtsProperties properties = enabledProperties();
        properties.getPresets().getCells().get(InterviewTone.GENTLE)
                .remove(InterviewType.FRIENDLY);

        assertThatThrownBy(() -> new TtsPresetRegistry(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GENTLE/FRIENDLY");
    }

    @ParameterizedTest
    @CsvSource({"stability,-0.01", "style,1.01", "speed,0.69"})
    void 프리셋_값이_범위를_벗어나면_기동에_실패한다(String field, double value) {
        TtsProperties properties = enabledProperties();
        TtsProperties.Preset preset = properties.getPresets().getCells()
                .get(InterviewTone.DIRECT).get(InterviewType.REALISTIC);
        setValue(preset, field, value);

        assertThatThrownBy(() -> new TtsPresetRegistry(properties).validate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DIRECT/REALISTIC " + field);
    }

    private TtsProperties enabledProperties() {
        TtsProperties properties = properties();
        properties.setEnabled(true);
        properties.getElevenlabs().setApiKey("api-key");
        properties.getStorage().setBucket("bucket");
        for (int index = 1; index <= 5; index++) {
            properties.getVoices().put(index, "voice-" + index);
        }
        return properties;
    }

    private TtsProperties properties() {
        TtsProperties properties = new TtsProperties();
        properties.getVoices().put(1, "voice-one");
        properties.getPresets().getCells().put(
                InterviewTone.GENTLE,
                row(
                        preset(0.78, 0.00, 0.98),
                        preset(0.76, 0.05, 1.00),
                        preset(0.80, 0.03, 0.99)
                )
        );
        properties.getPresets().getCells().put(
                InterviewTone.DIRECT,
                row(
                        preset(0.38, 0.42, 1.13),
                        preset(0.51, 0.30, 1.12),
                        preset(0.42, 0.48, 1.14)
                )
        );
        properties.getPresets().getCells().put(
                InterviewTone.PRESSURING,
                row(
                        preset(0.12, 0.90, 1.18),
                        preset(0.12, 0.98, 1.20),
                        preset(0.01, 1.00, 1.19)
                )
        );
        return properties;
    }

    private EnumMap<InterviewType, TtsProperties.Preset> row(
            TtsProperties.Preset friendly,
            TtsProperties.Preset realistic,
            TtsProperties.Preset meticulous
    ) {
        EnumMap<InterviewType, TtsProperties.Preset> result =
                new EnumMap<>(InterviewType.class);
        result.put(InterviewType.FRIENDLY, friendly);
        result.put(InterviewType.REALISTIC, realistic);
        result.put(InterviewType.METICULOUS, meticulous);
        return result;
    }

    private TtsProperties.Preset preset(double stability, double style, double speed) {
        TtsProperties.Preset result = new TtsProperties.Preset();
        result.setStability(stability);
        result.setStyle(style);
        result.setSpeed(speed);
        return result;
    }

    private PersonaSnapshot persona(
            int voiceIndex,
            InterviewTone tone,
            InterviewType type
    ) {
        return PersonaSnapshot.builder()
                .personaId(1L)
                .voiceIndex(voiceIndex)
                .tone(tone)
                .type(type)
                .build();
    }

    private TtsInputBuilder inputBuilder(TtsProperties properties) {
        SpokenTextResolver spokenTextResolver = new SpokenTextResolver(
                properties, new RepitMetrics(new SimpleMeterRegistry())
        );
        return new TtsInputBuilder(spokenTextResolver, properties);
    }

    private ChatQuestion question() {
        return ChatQuestion.builder()
                .content("레디스를 설명해주세요")
                .spokenText("레디스를 설명해주세요")
                .build();
    }

    private void setValue(TtsProperties.Preset preset, String field, double value) {
        switch (field) {
            case "stability" -> preset.setStability(value);
            case "style" -> preset.setStyle(value);
            case "speed" -> preset.setSpeed(value);
            default -> throw new IllegalArgumentException("알 수 없는 필드: " + field);
        }
    }
}
