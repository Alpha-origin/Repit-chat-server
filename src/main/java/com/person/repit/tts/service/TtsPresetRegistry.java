package com.person.repit.tts.service;

import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.InterviewType;
import com.person.repit.tts.config.TtsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@RequiredArgsConstructor
public class TtsPresetRegistry {
    private final TtsProperties properties;

    @PostConstruct
    void validate() {
        if (!properties.isEnabled()) {
            return;
        }
        requireText(properties.getElevenlabs().getApiKey(), "ELEVENLABS_API_KEY");
        requireText(properties.getStorage().getBucket(), "S3_BUCKET");
        for (int index = 1; index <= 5; index++) {
            requireText(properties.getVoices().get(index), "TTS_VOICE_ID_" + index);
        }
        for (InterviewTone tone : InterviewTone.values()) {
            for (InterviewType type : InterviewType.values()) {
                validate(tone, type, preset(tone, type));
            }
        }
    }

    public ResolvedPreset resolve(InterviewTone tone, InterviewType type) {
        TtsProperties.Preset preset = preset(tone, type);
        return new ResolvedPreset(
                preset.getStability(), preset.getStyle(), preset.getSpeed()
        );
    }

    private TtsProperties.Preset preset(InterviewTone tone, InterviewType type) {
        if (tone == null || type == null
                || properties.getPresets().getCells().get(tone) == null
                || properties.getPresets().getCells().get(tone).get(type) == null) {
            throw new IllegalStateException("TTS 프리셋이 누락되었습니다: " + tone + "/" + type);
        }
        return properties.getPresets().getCells().get(tone).get(type);
    }

    private void validate(
            InterviewTone tone,
            InterviewType type,
            TtsProperties.Preset preset
    ) {
        String cell = tone + "/" + type;
        requireRange(preset.getStability(), 0, 1, cell + " stability");
        requireRange(preset.getStyle(), 0, 1, cell + " style");
        requireRange(preset.getSpeed(), 0.7, 1.2, cell + " speed");
    }

    private void requireRange(double value, double min, double max, String name) {
        if (!Double.isFinite(value) || value < min || value > max) {
            throw new IllegalStateException(
                    "TTS 프리셋 범위를 벗어났습니다: " + name + "=" + value
            );
        }
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " 설정이 필요합니다");
        }
    }

    public record ResolvedPreset(double stability, double style, double speed) {
    }
}
