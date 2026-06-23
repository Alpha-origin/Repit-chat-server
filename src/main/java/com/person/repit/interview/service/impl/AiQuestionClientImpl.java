package com.person.repit.interview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
import com.person.repit.interview.service.AiQuestionClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Slf4j
@Component
public class AiQuestionClientImpl implements AiQuestionClient {

    private static final String ANTHROPIC_VERSION = "2023-06-01";

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String model;

    public AiQuestionClientImpl(
            ObjectMapper objectMapper,
            @Value("${anthropic.api-key}") String apiKey,
            @Value("${anthropic.model}") String model
    ) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.restClient = RestClient.builder()
                .baseUrl("https://api.anthropic.com")
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", ANTHROPIC_VERSION)
                .build();
    }

    @Override
    public FollowQuestionAiResponse decideFollowQuestion(FollowQuestionAiRequest request) {
        try {
            String body = restClient.post()
                    .uri("/v1/messages")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(createRequestBody(request))
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(body);

            log.info("AI RESPONSE = {}", response);

            String text = extractText(response);

            log.info("[AI RAW TEXT] {}", text);

            if (text == null || text.isBlank()) {
                return FollowQuestionAiResponse.notRequired();
            }

            String json = cleanJson(text);

            log.info("[AI CLEAN JSON] {}", json);

            FollowQuestionAiResponse result =
                    objectMapper.readValue(json, FollowQuestionAiResponse.class);

            log.info("[AI PARSED RESULT] required={}, content={}",
                    result.getRequired(),
                    result.getContent()
            );

            if (result.getRequired() == null) {
                result = FollowQuestionAiResponse.notRequired();
            }

            return result;

        } catch (Exception e) {
            log.error("[AI FAIL]", e);

            return FollowQuestionAiResponse.notRequired();
        }
    }

    private Map<String, Object> createRequestBody(FollowQuestionAiRequest request) {
        return Map.of(
                "model", model,
                "max_tokens", 500,
                "temperature", 0.3,
                "system", createSystemPrompt(),
                "messages", List.of(
                        Map.of(
                                "role", "user",
                                "content", createUserPrompt(request)
                        )
                )
        );
    }

    private String createSystemPrompt() {
        return """
                당신은 개발자 모의면접의 AI 면접관입니다.
                사용자의 답변을 보고 꼬리질문이 필요한지 판단하고, 필요하다면 꼬리질문을 생성하세요.

                규칙:
                1. 답변이 질문 의도에 충분히 맞고 구체적이면 required=false.
                2. 답변이 추상적이거나 근거가 부족하거나 추가 검증이 필요하면 required=true.
                3. 꼬리질문은 하나만 생성하세요.
                4. 꼬리질문은 사용자의 직전 답변에서 부족한 지점을 구체적으로 파고들어야 합니다.
                5. FRIENDLY는 부드럽고 격려하는 말투, NEUTRAL은 담백한 말투, STRESS는 직설적인 말투로 질문하세요.
                6. 면접 흐름상 이미 3단계 깊이 제한은 Chat 서버가 처리하므로, 당신은 현재 답변만 보고 판단하세요.
                7. 반드시 아래 JSON 형식만 응답하세요. 설명 문장, 마크다운, 코드블록은 절대 쓰지 마세요.

                응답 형식:
                {
                  "required": true 또는 false,
                  "intention": "꼬리질문 의도. required=false면 null",
                  "content": "꼬리질문 본문. required=false면 null"
                }
                """;
    }

    private String createUserPrompt(FollowQuestionAiRequest request) {
        return """
                [면접 정보]
                sessionId: %s
                interviewId: %d
                userId: %d
                personaId: %d
                personaType: %s

                [현재 질문]
                questionId: %d
                parentId: %s
                questionType: %s
                questionIntention: %s
                questionContent: %s

                [사용자 답변]
                responseTime: %s
                answerContent: %s

                위 정보를 바탕으로 꼬리질문 필요 여부를 판단하세요.
                """.formatted(
                request.getSessionId(),
                request.getInterviewId(),
                request.getUserId(),
                request.getPersonaId(),
                request.getPersonaType(),
                request.getQuestionId(),
                request.getParentId(),
                request.getQuestionType(),
                request.getQuestionIntention(),
                request.getQuestionContent(),
                request.getResponseTime(),
                request.getAnswerContent()
        );
    }

    private String extractText(JsonNode response) {
        if (response == null) {
            return null;
        }

        JsonNode content = response.get("content");

        if (content == null || !content.isArray()) {
            return null;
        }

        for (JsonNode item : content) {
            JsonNode type = item.get("type");
            JsonNode text = item.get("text");

            if (type != null && "text".equals(type.asText()) && text != null && text.isTextual()) {
                return text.asText();
            }
        }

        return null;
    }

    private String cleanJson(String text) {

        if (text == null) return null;

        String trimmed = text.trim();

        if (trimmed.startsWith("```")) {
            trimmed = trimmed
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();
        }

        int start = trimmed.indexOf("{");
        int end = trimmed.lastIndexOf("}");

        if (start != -1 && end != -1 && end > start) {
            trimmed = trimmed.substring(start, end + 1);
        }

        return trimmed;
    }
}