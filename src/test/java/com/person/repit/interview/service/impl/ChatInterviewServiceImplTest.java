package com.person.repit.interview.service.impl;

import com.person.repit.common.exception.InvalidRequestException;
import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.domain.CompanyContext;
import com.person.repit.interview.domain.PersonaSnapshot;
import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
import com.person.repit.interview.service.AiQuestionClient;
import com.person.repit.interview.service.InterviewResultDeliveryService;
import com.person.repit.interview.service.InterviewOperationLock;
import com.person.repit.interview.type.InterviewLevel;
import com.person.repit.interview.type.InterviewMajor;
import com.person.repit.interview.type.InterviewMode;
import com.person.repit.interview.type.InterviewRole;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.InterviewType;
import com.person.repit.interview.type.QuestionType;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.domain.AudioStatus;
import com.person.repit.tts.service.InterviewTtsScheduler;
import com.person.repit.tts.service.PersonaTtsProfileResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
class ChatInterviewServiceImplTest {

    private AiQuestionClient aiQuestionClient;
    private ReactiveRedisTemplate<String, Object> redisTemplate;
    private ReactiveValueOperations<String, Object> valueOperations;
    private ChatInterviewServiceImpl service;
    private InterviewTtsScheduler ttsScheduler;

    @BeforeEach
    void setUp() {
        aiQuestionClient = mock(AiQuestionClient.class);
        redisTemplate = mock(ReactiveRedisTemplate.class);
        valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ttsScheduler = mock(InterviewTtsScheduler.class);
        when(ttsScheduler.register(anyString(), any(), any(), anyInt(), anyInt()))
                .thenAnswer(invocation -> Mono.just(AudioState.builder()
                        .sessionId(invocation.getArgument(0))
                        .questionId(((ChatQuestion) invocation.getArgument(1)).getQuestionId())
                        .status(AudioStatus.QUEUED)
                        .build()));

        InterviewOperationLock lock = mock(InterviewOperationLock.class);
        when(lock.execute(anyString(), any())).thenAnswer(invocation ->
                ((java.util.function.Supplier<Mono<?>>) invocation.getArgument(1)).get());
        InterviewResultDeliveryService delivery = mock(InterviewResultDeliveryService.class);
        when(delivery.resume(anyString())).thenReturn(Mono.empty());
        service = new ChatInterviewServiceImpl(
                aiQuestionClient,
                redisTemplate,
                delivery,
                lock,
                new RepitMetrics(new SimpleMeterRegistry()),
                mock(PersonaTtsProfileResolver.class),
                ttsScheduler
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
        assertThat(session.getCompany()).isNull();
        assertThat(session.getQuestions())
                .extracting(ChatQuestion::getCategory, ChatQuestion::getIntention,
                        ChatQuestion::getSpokenText)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(
                                "tech_choice", "선택 근거를 설명할 수 있는지", "발음 질문"
                        ),
                        org.assertj.core.groups.Tuple.tuple("problem_solving", null, "발음 질문")
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

    @Test
    void 준비_요청의_기업을_세션에_저장한다() {
        var request = prepareRequest();
        var company = new ChatInterviewPrepareRequest.Company();
        company.setDisplayName("샘플커머스");
        company.setInterviewerBrief("주문 동시성과 오너십을 다루는 샘플기업");
        request.setCompany(company);
        when(valueOperations.setIfAbsent(anyString(), any(), eq(Duration.ofHours(3))))
                .thenReturn(Mono.just(true));

        service.prepareInterview(request).block();

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(valueOperations).setIfAbsent(anyString(), captor.capture(), eq(Duration.ofHours(3)));
        var saved = (ChatInterviewSession) captor.getValue();
        assertThat(saved.getCompany().getDisplayName()).isEqualTo(company.getDisplayName());
        assertThat(saved.getCompany().getInterviewerBrief()).isEqualTo(company.getInterviewerBrief());
    }

    @Test
    void 중복_준비_요청으로_기존_세션의_기업을_덮어쓰지_않는다() {
        var existing = interviewSession();
        existing.setCompany(CompanyContext.builder().displayName("최초기업")
                .interviewerBrief("최초 요약").build());
        when(valueOperations.setIfAbsent(anyString(), any(), eq(Duration.ofHours(3))))
                .thenReturn(Mono.just(false));
        when(valueOperations.get("chat:interview:session-id")).thenReturn(Mono.just(existing));
        var request = prepareRequest();
        var company = new ChatInterviewPrepareRequest.Company();
        company.setDisplayName("다른기업");
        company.setInterviewerBrief("다른 요약");
        request.setCompany(company);

        service.prepareInterview(request).block();

        assertThat(existing.getCompany().getDisplayName()).isEqualTo("최초기업");
        verify(valueOperations, never()).set(anyString(), any(), any(Duration.class));
        verify(ttsScheduler, never()).register(anyString(), any(), any(), anyInt(), anyInt());
    }

    @ParameterizedTest
    @EnumSource(InterviewRole.class)
    void 다대일_면접의_모든_직책에_세션의_기업을_전달한다(InterviewRole role) {
        var session = interviewSession();
        session.setMode(InterviewMode.MULTI);
        session.setCompany(CompanyContext.builder().displayName("샘플커머스")
                .interviewerBrief("주문 동시성과 오너십을 다루는 샘플기업").build());
        session.setPersonas(Map.of(3L, PersonaSnapshot.builder().personaId(3L)
                .role(role).type(InterviewType.FRIENDLY).tone(InterviewTone.GENTLE)
                .major(InterviewMajor.BACKEND).level(InterviewLevel.NORMAL).build()));
        mockSession(session);
        when(aiQuestionClient.decideFollowQuestion(any()))
                .thenReturn(Mono.just(FollowQuestionAiResponse.notRequired()));

        service.submitAnswer("session-id", answerRequest()).block();

        var captor = ArgumentCaptor.forClass(FollowQuestionAiRequest.class);
        verify(aiQuestionClient).decideFollowQuestion(captor.capture());
        assertThat(captor.getValue().getRole()).isEqualTo(role);
        assertThat(captor.getValue().getCompanyName()).isEqualTo("샘플커머스");
        assertThat(captor.getValue().getCompanyBrief()).isEqualTo(session.getCompany().getInterviewerBrief());
    }

    @Test
    void 기업이_없는_기존_세션은_AI_요청에도_기업을_넣지_않는다() {
        mockSession(interviewSession());
        when(aiQuestionClient.decideFollowQuestion(any()))
                .thenReturn(Mono.just(FollowQuestionAiResponse.notRequired()));

        service.submitAnswer("session-id", answerRequest()).block();

        var captor = ArgumentCaptor.forClass(FollowQuestionAiRequest.class);
        verify(aiQuestionClient).decideFollowQuestion(captor.capture());
        assertThat(captor.getValue().getCompanyName()).isNull();
        assertThat(captor.getValue().getCompanyBrief()).isNull();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void 중복_질문_ID는_면접관_목록_유무와_관계없이_저장_전에_거절한다(boolean withPersonas) {
        var request = prepareRequest();
        request.getQuestions().get(1).setId(request.getQuestions().getFirst().getId());
        if (withPersonas) {
            var persona = new ChatInterviewPrepareRequest.Persona();
            persona.setPersonaId(3L);
            persona.setRole(InterviewRole.HR);
            persona.setType(InterviewType.FRIENDLY);
            persona.setTone(InterviewTone.GENTLE);
            persona.setLevel(InterviewLevel.NORMAL);
            request.setPersonas(List.of(persona));
        }

        StepVerifier.create(service.prepareInterview(request))
                .expectErrorSatisfies(error -> assertThat(error)
                        .isInstanceOf(InvalidRequestException.class)
                        .hasMessage("질문 ID가 중복되었습니다."))
                .verify();
        verifyNoInteractions(redisTemplate, ttsScheduler, aiQuestionClient);
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
        question.setSpokenText("발음 질문");
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
