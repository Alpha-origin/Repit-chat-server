package com.person.repit.tts.domain;

public record ResolvedSpokenText(String text, boolean usedFallback, String reason) {
}
