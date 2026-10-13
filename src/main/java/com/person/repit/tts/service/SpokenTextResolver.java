package com.person.repit.tts.service;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.tts.config.TtsProperties;
import com.person.repit.tts.domain.ResolvedSpokenText;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
public class SpokenTextResolver {
    private static final Pattern CONVERSION_TARGET = Pattern.compile("[A-Za-z0-9/+#@%`]");
    private static final Pattern LATIN = Pattern.compile("[A-Za-z]");
    private static final Pattern TAG_CHARS = Pattern.compile("[\\[\\]{}<>`]");
    private static final Pattern DIGITS = Pattern.compile("[0-9]");

    private final TtsProperties properties;
    private final RepitMetrics metrics;

    public ResolvedSpokenText resolve(String content, String spokenText) {
        if (!StringUtils.hasText(content)) {
            return new ResolvedSpokenText("", true, "EMPTY_CONTENT");
        }
        if (!CONVERSION_TARGET.matcher(content).find()) {
            return new ResolvedSpokenText(content, false, "NO_CONVERSION_NEEDED");
        }
        if (!StringUtils.hasText(spokenText)) {
            return fallback(content, "EMPTY");
        }

        String normalized = spokenText.trim();
        if (LATIN.matcher(normalized).find()) {
            return fallback(content, "LATIN_REMAINS");
        }
        if (TAG_CHARS.matcher(normalized).find()) {
            return fallback(content, "TAG_CHARS");
        }

        int contentLength = withoutWhitespace(content).length();
        int spokenLength = withoutWhitespace(normalized).length();
        double ratio = contentLength == 0 ? 0 : (double) spokenLength / contentLength;
        if (ratio < properties.getSpokenText().getMinLengthRatio()
                || ratio > properties.getSpokenText().getMaxLengthRatio()) {
            return fallback(content, "LENGTH_RATIO");
        }
        if (DIGITS.matcher(normalized).find()) {
            metrics.spokenTextDigitsRemain();
        }
        return new ResolvedSpokenText(normalized, false, "OK");
    }

    private ResolvedSpokenText fallback(String content, String reason) {
        metrics.spokenTextFallback(reason);
        return new ResolvedSpokenText(content, true, reason);
    }

    private String withoutWhitespace(String value) {
        return value.replaceAll("\\s+", "");
    }
}
