package com.person.repit.interview.service.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;

import java.util.Set;

public record CompanyFollowUpContent(String intention, String content, String spokenText, String expectedAnswer) {
    private static final Set<String> FIELDS = Set.of("intention", "content", "spokenText", "expectedAnswer");

    public CompanyFollowUpContent {
        intention = required(intention, 200);
        content = required(content, 200);
        expectedAnswer = required(expectedAnswer, 1_000);
        if (spokenText != null) {
            spokenText = spokenText.strip();
            if (spokenText.isEmpty()) {
                spokenText = null;
            } else {
                spokenText = required(spokenText, 1_000);
            }
        }
    }

    public static CompanyFollowUpContent read(JsonNode node) {
        if (node == null || !node.isObject() || node.size() != FIELDS.size()
                || !FIELDS.stream().allMatch(node::has)) {
            throw new IllegalArgumentException("Unexpected company follow-up fields");
        }
        return new CompanyFollowUpContent(text(node, "intention"), text(node, "content"),
                node.get("spokenText").isNull() ? null : text(node, "spokenText"), text(node, "expectedAnswer"));
    }

    public FollowQuestionAiResponse merge(FollowQuestionAiResponse decision) {
        FollowQuestionAiResponse result = new FollowQuestionAiResponse();
        result.setScore(decision.getScore());
        result.setRequired(decision.getRequired());
        result.setIntention(intention);
        result.setContent(content);
        result.setSpokenText(spokenText);
        result.setExpectedAnswer(expectedAnswer);
        return result;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (!value.isTextual()) {
            throw new IllegalArgumentException("Company follow-up fields must be strings");
        }
        return value.textValue();
    }

    private static String required(String value, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Blank company follow-up text");
        }
        String normalized = value.strip();
        if (normalized.codePointCount(0, normalized.length()) > max) {
            throw new IllegalArgumentException("Company follow-up text exceeds limit");
        }
        return normalized;
    }
}
