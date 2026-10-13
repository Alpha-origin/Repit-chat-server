package com.person.repit.tts.service;

import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.ResolvedSpokenText;
import com.person.repit.tts.domain.TtsInput;
import com.person.repit.tts.domain.TtsProfile;
import com.person.repit.tts.domain.VoiceSettings;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@Component
@RequiredArgsConstructor
public class TtsInputBuilder {
    private final SpokenTextResolver spokenTextResolver;
    private final TtsProperties properties;

    public TtsInput build(ChatQuestion question, TtsProfile profile) {
        ResolvedSpokenText resolved = spokenTextResolver.resolve(
                question.getContent(), question.getSpokenText()
        );
        String text = applyPauses(resolved.text(), profile.getPauseAfterSentenceMs());
        VoiceSettings settings = new VoiceSettings(
                profile.getStability(),
                profile.getSimilarityBoost(),
                profile.getStyle(),
                profile.getSpeed(),
                profile.isUseSpeakerBoost()
        );
        String hashSource = text + profile.getVoiceId() + profile.getModelId()
                + settings + profile.getOutputFormat() + profile.getProfileVersion();
        return new TtsInput(
                text,
                profile.getVoiceId(),
                profile.getModelId(),
                profile.getOutputFormat(),
                settings,
                sha256(hashSource).substring(0, 16)
        );
    }

    private String applyPauses(String text, int pauseMs) {
        if (!properties.getPause().isEnabled() || pauseMs <= 0) {
            return text;
        }
        String marker = "<break time=\"" + pauseMs / 1000.0 + "s\" />";
        StringBuilder result = new StringBuilder();
        int inserted = 0;
        for (int index = 0; index < text.length(); index++) {
            char current = text.charAt(index);
            result.append(current);
            boolean boundary = current == '.' || current == '?' || current == '!';
            boolean hasMoreText = index + 1 < text.length();
            if (boundary && hasMoreText && inserted < properties.getPause().getMaxPerQuestion()) {
                result.append(' ').append(marker);
                inserted++;
            }
        }
        return result.toString();
    }

    private String sha256(String value) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(bytes);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", exception);
        }
    }
}
