package com.person.repit.interview.service.impl;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
import com.person.repit.interview.service.AiQuestionClient;
import com.person.repit.interview.service.ApiServerClient;
import com.person.repit.interview.type.InterviewLevel;
import com.person.repit.interview.type.InterviewMajor;
import com.person.repit.interview.type.InterviewMode;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.InterviewType;
import com.person.repit.interview.type.QuestionType;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ChatInterviewServiceImplTest {

    private AiQuestionClient aiQuestionClient;
    private ReactiveRedisTemplate<String, Object> redisTemplate;
    private ReactiveValueOperations<String, Object> valueOperations;
    private ChatInterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        aiQuestionClient = mock(AiQuestionClient.class);
        redisTemplate = mock(ReactiveRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        service = new ChatInterviewServiceImpl(
                aiQuestionClient,
                redisTemplate,
                mock(ApiServerClient.class),
                new RepitMetrics(new SimpleMeterRegistry())
        );
    }

    @Test
    void 세션_생성에서_카테고리와_의도를_분리하고_빈_의도는_null로_저장한다() {
        when(valueOperations.setIfAbsent(
                eq("chat:interview:session-id"),
                any(ChatInterviewSession.class),
                eq(Duration.ofHours(3))
        )).thenReturn(Mono.just(true));

        service.prepareInterview(prepareRequest()).block();

        ArgumentCaptor<Object> sessionCaptor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).setIfAbsent(
                eq("chat:interview:session-id"),
                sessionCaptor.capture(),
                eq(Duration.ofHours(3))
        );

        ChatInterviewSession session = (ChatInterviewSession) sessionCaptor.getValue();
        assertThat(session.getQuestions())
                .extracting(ChatQuestion::getCategory, ChatQuestion::getIntention)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("tech_choice", "선택 근거를 설명할 수 있는지"),
                        org.assertj.core.groups.Tuple.tuple("problem_solving", null)
                );
    }

    @Test
    void 꼬리질문은_AI가_만든_의도와_부모_카테고리를_저장한다() {
        ChatInterviewSession session = interviewSession();
        mockSession(session);

        FollowQuestionAiResponse aiResponse = followResponse("락 범위를 설명할 수 있는지");
        when(aiQuestionClient.decideFollowQuestion(any())).thenReturn(Mono.just(aiResponse));

        service.submitAnswer("session-id", answerRequest()).block();

        ChatQuestion followQuestion = session.getQuestions().get(1);
        assertThat(followQuestion.getCategory()).isEqualTo("tech_choice");
        assertThat(followQuestion.getIntention()).isEqualTo("락 범위를 설명할 수 있는지");
    }

    @Test
    void AI_의도가_비면_꼬리질문에_부모_의도를_저장한다() {
        ChatInterviewSession session = interviewSession();
        mockSession(session);

        FollowQuestionAiResponse aiResponse = followResponse(" ");
        when(aiQuestionClient.decideFollowQuestion(any())).thenReturn(Mono.just(aiResponse));

        service.submitAnswer("session-id", answerRequest()).block();

        ChatQuestion followQuestion = session.getQuestions().get(1);
        assertThat(followQuestion.getIntention()).isEqualTo("분산락 선택 근거를 설명할 수 있는지");
    }

    private ChatInterviewPrepareRequest prepareRequest() {
        ChatInterviewPrepareRequest request = new ChatInterviewPrepareRequest();
        request.setSessionId("session-id");
        request.setInterviewId(1L);
        request.setUserId(2L);
        request.setStatus(InterviewStatus.IN_PROGRESS);
        request.setMode(InterviewMode.SOLO);
        request.setPersonality(InterviewType.FRIENDLY);
        request.setTone(InterviewTone.GENTLE);
        request.setMajor(InterviewMajor.BACKEND);
        request.setLevel(InterviewLevel.NORMAL);
        request.setQuestions(List.of(
                prepareQuestion(1L, "tech_choice", "선택 근거를 설명할 수 있는지"),
                prepareQuestion(2L, "problem_solving", " ")
        ));
        return request;
    }

    private ChatInterviewPrepareRequest.Question prepareQuestion(
            Long id,
            String category,
            String intention
    ) {
        ChatInterviewPrepareRequest.Question question = new ChatInterviewPrepareRequest.Question();
        question.setId(id);
        question.setCategory(category);
        question.setQuestion("질문");
        question.setIntention(intention);
        question.setPersonaId(3L);
        return question;
    }

    private ChatInterviewSession interviewSession() {
        ChatQuestion currentQuestion = ChatQuestion.builder()
                .questionId(1L)
                .type(QuestionType.ORIGINAL)
                .category("tech_choice")
                .intention("분산락 선택 근거를 설명할 수 있는지")
                .askedByPersonaId(3L)
                .build();
        ChatQuestion nextQuestion = ChatQuestion.builder()
                .questionId(2L)
                .type(QuestionType.ORIGINAL)
                .category("problem_solving")
                .build();

        return ChatInterviewSession.builder()
                .sessionId("session-id")
                .interviewId(1L)
                .userId(2L)
                .personality(InterviewType.FRIENDLY)
                .tone(InterviewTone.GENTLE)
                .major(InterviewMajor.BACKEND)
                .level(InterviewLevel.NORMAL)
                .questions(new ArrayList<>(List.of(currentQuestion, nextQuestion)))
                .answers(new ArrayList<>())
                .currentQuestionIndex(0)
                .build();
    }

    private void mockSession(ChatInterviewSession session) {
        when(valueOperations.get("chat:interview:session-id")).thenReturn(Mono.just(session));
        when(valueOperations.set(anyString(), any(), eq(Duration.ofHours(3))))
                .thenReturn(Mono.just(true));
    }

    private FollowQuestionAiResponse followResponse(String intention) {
        FollowQuestionAiResponse response = new FollowQuestionAiResponse();
        response.setRequired(true);
        response.setIntention(intention);
        response.setContent("꼬리질문");
        response.setExpectedAnswer("꼬리질문 답안");
        return response;
    }

    private ChatAnswerRequest answerRequest() {
        return ChatAnswerRequest.builder()
                .questionId(1L)
                .responseTime(10)
                .content("답변")
                .build();
    }
}
