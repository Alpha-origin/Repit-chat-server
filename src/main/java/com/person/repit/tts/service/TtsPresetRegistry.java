package com.person.repit.tts.service;

import com.person.repit.interview.type.InterviewMode;
import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.InterviewType;
import com.person.repit.tts.config.TtsProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;

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
        Arrays.stream(InterviewMode.values()).forEach(mode ->
                Arrays.stream(InterviewTone.values()).forEach(tone -> preset(mode, tone)));
        Arrays.stream(InterviewTone.values()).forEach(tone -> adjustment(tone));
        Arrays.stream(InterviewType.values()).forEach(type -> adjustment(type));
    }

    public ResolvedPreset resolve(InterviewMode mode, InterviewTone tone, InterviewType type) {
        TtsProperties.Preset base;
        try {
            base = preset(mode, tone);
        } catch (IllegalStateException exception) {
            TtsProperties.Defaults defaults = properties.getDefaults();
            return new ResolvedPreset(
                    defaults.getStability(), defaults.getStyle(), defaults.getSpeed()
            );
        }

        TtsProperties.Preset toneAdjustment = adjustment(tone);
        TtsProperties.Preset typeAdjustment = adjustment(type);
        return new ResolvedPreset(
                round(clamp(base.getStability() + toneAdjustment.getStability()
                        + typeAdjustment.getStability(), 0, 1)),
                round(clamp(base.getStyle() + toneAdjustment.getStyle()
                        + typeAdjustment.getStyle(), 0, 1)),
                round(clamp(base.getSpeed() + toneAdjustment.getSpeed()
                        + typeAdjustment.getSpeed(), properties.getClamp().getSpeedMin(),
                        properties.getClamp().getSpeedMax()))
        );
    }

    private TtsProperties.Preset preset(InterviewMode mode, InterviewTone tone) {
        if (mode == null || tone == null || properties.getPresets().getBase().get(mode) == null
                || properties.getPresets().getBase().get(mode).get(tone) == null) {
            throw new IllegalStateException("TTS 기본 프리셋이 누락되었습니다: " + mode + "/" + tone);
        }
        return properties.getPresets().getBase().get(mode).get(tone);
    }

    private TtsProperties.Preset adjustment(InterviewTone tone) {
        TtsProperties.Preset value = properties.getPresets().getToneAdjust().get(tone);
        if (value == null) {
            throw new IllegalStateException("TTS 말투 보정값이 누락되었습니다: " + tone);
        }
        return value;
    }

    private TtsProperties.Preset adjustment(InterviewType type) {
        TtsProperties.Preset value = properties.getPresets().getTypeAdjust().get(type);
        if (value == null) {
            throw new IllegalStateException("TTS 성향 보정값이 누락되었습니다: " + type);
        }
        return value;
    }

    private void requireText(String value, String name) {
        if (!StringUtils.hasText(value)) {
            throw new IllegalStateException(name + " 설정이 필요합니다");
        }
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private double round(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    public record ResolvedPreset(double stability, double style, double speed) {
    }
}
