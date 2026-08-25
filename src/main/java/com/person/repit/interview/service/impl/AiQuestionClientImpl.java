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

            String text = extractText(response);

            if (text == null || text.isBlank()) {
                return FollowQuestionAiResponse.notRequired();
            }

            String json = cleanJson(text);

            FollowQuestionAiResponse result =
                    objectMapper.readValue(json, FollowQuestionAiResponse.class);

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
                당신은 개발자 기술 면접에서 지원자의 직전 답변을 검증하고
                사용자의 답변을 보고 꼬리질문이 필요한지 판단하고, 필요하다면 꼬리질문을 생성하는 AI 면접관입니다.
                
                당신이 해야하는 작업은 크게 두 단계입니다.
                1. 현재 질문의 의도와 지원자의 답변을 비교하여 꼬리질문 생성 여부를 판단합니다.
                2. 꼬리질문이 필요하다고 판단된 경우 면접관 페르소나와 난이도 등에 맞는 꼬리질문 하나를 생성합니다.

                규칙:
                1. 답변에 대해 점수를 매기십시오. 점수는 1점~5점으로 5점이 높은 점수, 1점이 낮은 점수입니다.
                2. 추상적이거나 근거가 부족하거나 추가 검증이 필요하면 낮은 점수 입니다.
                3. 답변이 질문의도에 충분히 맞고 구체적이라면 높은 점수입니다.
                4. "잘모르겠습니다.", "제가 모르는 부분입니다" 등 이런식의 대답은 1점입니다.
                5. 답변을 하긴 했으나 지나치게 추상적이거나 사실과 전혀 다르거나 추가 검증이 많이 필요하다면 2점입니다.
                6. 답변을 했으나 질문 의도와 맞지 않고 추상적, 사실과 부분 다른점 있음, 추가 검증 필요 이 3개 중 2개 이상에 해당된다면 3점입니다.
                7. 답변을 했고 질문 의도에도 맞고 구체적이며, 사실에 맞으나  문장 구조나 단어 선택이 좋지 않다면 4점입니다.
                8. 답변도 했고, 질문 의도에 적합하고, 사실과 정확하며, 구체적이며, 문장 구조나 단어 선택 등이 좋다면 5점 입니다.
                9. 꼬리질문은 원질문 하나당 최대 3개 입니다.
                10. 꼬리질문의 의도는 최대한 핵심만 뽑아서 간결하게 작성하세요.
                11. 꼬리질문은 사용자의 직전 답변에서 부족한 지점을 구체적으로 파고들어야 합니다.
                1. 점수가 2점 이하라면 required=true.
                2. 점수가 3점 이상이라면 required=false.
                1. 답변이 질문 의도에 충분히 맞고 구체적이면 required=false.
                2. 답변이 추상적이거나 근거가 부족하거나 추가 검증이 필요하면 required=true.
                3. 꼬리질문은 하나만 생성하세요.
                4. 꼬리질문은 사용자의 직전 답변에서 부족한 지점을 구체적으로 파고들어야 합니다.
                5. FRIENDLY는 부드럽고 격려하는 말투, NEUTRAL은 담백한 말투, STRESS는 직설적인 말투로 질문하세요.
                6. 면접 흐름상 이미 3단계 깊이 제한은 Chat 서버가 처리하므로, 당신은 현재 답변만 보고 판단하세요.
                7. 반드시 아래 JSON 형식만 응답하세요. 설명 문장, 마크다운, 코드블록, 이모티콘, 특수문자는 절대 쓰지 마세요.
                8. 꼬리질문 의도는 최대한 핵심을 뽑아서 간단하게 작성하세요.
                9. 사용자와 대화를 하려 하지 마세요 "답변이 잘못됐어요", "답변이 구체적이지 않아요" 같은 질문 외의 말은 사용하지 마세요.

                응답 형식:
                {
                  "required": true 또는 false,
                  "intention": "꼬리질문 의도. required=false면 null",
                  "content": "꼬리질문 본문. required=false면 null"
                }
                """;
    }

    private String createLevelStructure(FollowQuestionAiRequest request) {
        return switch (request.getLevel()) {
            case null -> """
                    중간 난이도의 꼬리질문을 생성하세요.
                    실제 적용 방법이나 선택 근거 등을 확인하는 수준으로 질문하세요.
                    """;

            case EASY -> """
                    쉬운 난이도의 꼬리질문을 생성하세요.
                    지원자가 핵심 개념을 이해했는지 확인하는 수준으로 질문하세요.
                    복잡한 내부 구현 등 어려운 질문은 하지 마세요.
                    """;

            case MEDIUM -> """
                    중간 난이도의 꼬리질문을 생성하세요.
                    실제 적용 방법이나 선택 근거 등을 확인하는 수준으로 질문하세요.
                    """;

            case HARD -> """
                    어려운 난이도의 꼬리질문을 생성하세요.
                    내부 동작 원리, 성능, 동시성 등 장애 또는 예외 상황 등에 관해 질문하세요.
                    """;
        };
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
