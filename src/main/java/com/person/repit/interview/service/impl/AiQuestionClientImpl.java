package com.person.repit.interview.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
import com.person.repit.interview.service.AiRequestConcurrencyLimiter;
import com.person.repit.interview.service.AiQuestionClient;
import io.netty.handler.timeout.ReadTimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Map;
import java.util.concurrent.RejectedExecutionException;

@Slf4j
@Component
public class AiQuestionClientImpl implements AiQuestionClient {

    private final WebClient webClient;
    private final ObjectMapper objectMapper;
    private final String model;
    private final RepitMetrics metrics;
    private final AiRequestConcurrencyLimiter concurrencyLimiter;

    public AiQuestionClientImpl(
            ObjectMapper objectMapper,
            @Value("${anthropic.model}") String model,
            @Qualifier("anthropicWebClient") WebClient webClient,
            RepitMetrics metrics,
            AiRequestConcurrencyLimiter concurrencyLimiter
    ) {
        this.objectMapper = objectMapper;
        this.model = model;
        this.metrics = metrics;
        this.webClient = webClient;
        this.concurrencyLimiter = concurrencyLimiter;
    }

    @Override
    public Mono<FollowQuestionAiResponse> decideFollowQuestion(FollowQuestionAiRequest request) {
        return metrics.recordAnthropicRequest(
                concurrencyLimiter.execute(executeRequest(request))
                )
                .doOnError(
                        exception -> !isExpectedFallback(exception),
                        exception -> log.error("[AI FAIL]", exception)
                )
                .onErrorReturn(FollowQuestionAiResponse.notRequired());
    }

    private boolean isExpectedFallback(Throwable exception) {
        return exception instanceof RejectedExecutionException
                || exception.getCause() instanceof ReadTimeoutException;
    }

    private Mono<FollowQuestionAiResponse> executeRequest(FollowQuestionAiRequest request) {
        return webClient.post()
                .uri("/v1/messages")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(createRequestBody(request))
                .retrieve()
                .bodyToMono(String.class)
                .map(this::parseResponse);
    }

    private FollowQuestionAiResponse parseResponse(String body) {
        try {
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

        } catch (RuntimeException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Anthropic 응답 처리에 실패했습니다.", exception);
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
                
                입력으로 다음 정보가 제공됩니다.
                - 원질문의 ID
                - 원질문의 의도(intention)
                - 원질문의 내용(question)
                - 원질문의 기대 답변(expectedAnswer)
                - 지원자의 직전 답변
                - 면접관 페르소나 : FRIENDLY, NEUTRAL, STRESS
                - 면접 난이도
                
                다음 절차에 따라 평가하세요.
                1. expectedAnswer를 원질문의 검증 포인트로 사용하여 지원자의 직전 답변과 비교합니다.
                2. 답변의 적합성 정확성, 구체성, 논리성, 근거의 충분성을 평가하여 0점부터 5점까지 점수를 부여합니다.
                3. 점수와 답변의 검증 필요성을 바탕으로 꼬리질문 생성 여부를 결정합니다.
                4. 꼬리질문이 필요하면 직전 답변에서 가장 부족하거나 불확실한 지점 하나를 선택하여 질문 하나만 생성합니다.

                원질문 사용 규칙:
                - intention은 tech_choice 등의 질문 분류 정보로 참고하되, 기대 답변으로 해석하지 않습니다.
                - expectedAnswer는 지원자의 답변과 글자 그대로 일치해야 하는 정답이 아니라, 원질문이 확인하려는 핵심을 판단하는 기준입니다.
                - 꼬리질문에서도 원질문의 검증 포인트를 유지하고 질문의 초점을 다른 기술이나 주제로 바꾸지 않습니다.
                - 원질문, expectedAnswer, 지원자의 답변에 없는 기술 스택, 수치, 장애 상황, 구현 방식을 새로 지어내지 않습니다.
                
                점수 기준:
                - 0점 : "잘 모르겠습니다.", "제가 모르는 부분입니다" 등 모든다고 명시한 경우
                    - "잘 모르겠지만 ~~인것 같습니다.", "~~인것 같습니다" 등은 0점이 아닙니다.
                - 1점 : 질문과 거의 관련이 없거나 대부분 사실과 다른 답변
                - 2점 : 답변은 했지만 지나치게 추상적이거나, 핵심 근거가 부족하거나, 사실과 다른 부분이 있어 추가 검증이 필요한 경우
                - 3점 : 질문 의도에 대체로 부합하지만 일부 내용이 추상적이거나 부정확하여 보완할 부분이 있는 경우
                - 4점 : 질문 의도에 부합하고 구체적이며 사실에도 맞지만, 표현이나 문장 구조가 다수 불명확하거나 부족한 경우
                - 5점 : 질문 의도에 정확히 부합하고 사실관계, 구체성, 논리, 표현이 모두 충분한 경우
                
                꼬리질문 판단 기준:
                - 0점 또는 3점 이상: required = false
                - 1점 또는 2점 : required = true
                
                단, 꼬리질문을 생성 할 때 다음 원칙을 무조건 지키세요.
                - 꼬리질문은 지원자의 직전 답변에 실제로 포함된 부족한 지점을 구체적으로 파고 들어야 합니다.
                - 답변과 무고나한 새로운 주제로 확장하지 않습니다.
                - 한 번의 응답에는 꼬리질문을 하나만 생성합니다.
                - 꼬리질문의 의도는 핵심만 간결하게 작성합니다.
                - 현재 답변만 평가하며 이전 꼬리질문의 깊이나 개수는 고려하지 않습니다.
                
                페르소나별 말투:
                - FRIENDLY : 부드럽고 격려하는 말투
                - NEUTRAL : 감정 표현 없이 담백하고 명확한 말투
                - STRESS : 불필요한 완곡 표현 없이 직설적이고 압박감 있는 말투

                출력 규칙:
                - 반드시 유효한 JSON 객체 하나만 출력합니다.
                - 설명, 마크다운, 코드 블록, 인사말 등 JSON 이외의 내용은 출력하지 않습니다.
                - JSON 문자열 내부에는 자연스러운 문장부호를 사용할 수 있습니다.
                - required가 false이면 intention, content, expectedAnswer는 반드시 null로 설정합니다.
                - required가 true이면 intention은 입력받은 questionIntention과 동일해야 합니다.
                - required가 true이면 content와 expectedAnswer는 비어 있지 않은 문자열이어야 합니다.
                - expectedAnswer에는 생성한 꼬리질문이 확인하려는 핵심 답변을 작성합니다.
                - 사용자말에 대답을 하거나 반응하면 안됩니다.

                응답 형식:
                {
                  "score": 꼬리질문 점수 (0~5점)
                  "required": true 또는 false,
                  "intention": "입력받은 questionIntention. required=false면 null",
                  "content": "꼬리질문 본문. required=false면 null",
                  "expectedAnswer": "꼬리질문의 기대 답변. required=false면 null"
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

                [현재 질문]
                questionId: %d
                parentId: %s
                questionType: %s
                questionIntention: %s
                questionContent: %s
                expectedAnswer: %s

                [사용자 답변]
                responseTime: %s
                answerContent: %s

                위 정보를 바탕으로 꼬리질문 필요 여부를 판단하세요.
                """.formatted(
                request.getSessionId(),
                request.getInterviewId(),
                request.getUserId(),
                request.getPersonaId(),
                request.getQuestionId(),
                request.getParentId(),
                request.getQuestionType(),
                request.getQuestionIntention(),
                request.getQuestionContent(),
                request.getExpectedAnswer(),
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
