package com.person.repit.interview.service.impl;

import com.person.repit.common.metrics.RepitMetrics;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.exception.InterviewSessionAlreadyExistsException;
import com.person.repit.interview.service.*;
import com.person.repit.interview.type.*;
import com.person.repit.tts.domain.AudioState;
import com.person.repit.tts.service.InterviewTtsScheduler;
import com.person.repit.tts.service.PersonaTtsProfileResolver;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class ChatInterviewIdentityTest {
    private final ReactiveRedisTemplate<String, Object> redis = mock(ReactiveRedisTemplate.class);
    private final ReactiveValueOperations<String, Object> values = mock(ReactiveValueOperations.class);
    private final InterviewTtsScheduler tts = mock(InterviewTtsScheduler.class);
    private final AiQuestionClient ai = mock(AiQuestionClient.class);
    private ChatInterviewServiceImpl service;

    @BeforeEach
    void setUp() {
        when(redis.opsForValue()).thenReturn(values);
        service = new ChatInterviewServiceImpl(ai, redis, mock(InterviewResultDeliveryService.class),
                mock(InterviewOperationLock.class), new RepitMetrics(new SimpleMeterRegistry()),
                mock(PersonaTtsProfileResolver.class), tts);
    }

    @ParameterizedTest
    @EnumSource(InterviewMode.class)
    void 준비시_API_식별자를_세션과_응답에_그대로_보존한다(InterviewMode mode) {
        var request = request(mode);
        when(values.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(Mono.just(true));
        when(tts.register(anyString(), any(), any(), anyInt(), anyInt()))
                .thenReturn(Mono.just(AudioState.builder().build()));

        var response = service.prepareInterview(request).block(Duration.ofSeconds(2));

        var saved = ArgumentCaptor.forClass(Object.class);
        verify(values).setIfAbsent(eq("chat:interview:api-session"), saved.capture(), eq(Duration.ofHours(3)));
        var session = (ChatInterviewSession) saved.getValue();
        assertThat(session.getSessionId()).isEqualTo(request.getSessionId());
        assertThat(session.getInterviewId()).isEqualTo(request.getInterviewId());
        assertThat(session.getUserId()).isEqualTo(request.getUserId());
        assertThat(session.getMode()).isEqualTo(mode);
        assertThat(response.getSessionId()).isEqualTo(session.getSessionId());
        assertThat(response.getInterviewId()).isEqualTo(session.getInterviewId());
        assertThat(response.getUserId()).isEqualTo(session.getUserId());
        assertThat(response.getMode()).isEqualTo(mode);
        verifyNoInteractions(ai);
    }

    @ParameterizedTest
    @EnumSource(InterviewMode.class)
    void 동일한_준비요청_재시도는_기존_세션을_그대로_반환한다(InterviewMode mode) {
        var existing = existing(mode, 101L, 202L);
        mockExisting(existing);
        var response = service.prepareInterview(request(mode)).block(Duration.ofSeconds(2));
        assertThat(response.getSessionId()).isEqualTo(existing.getSessionId());
        assertThat(response.getInterviewId()).isEqualTo(101L);
        assertThat(response.getUserId()).isEqualTo(202L);
        assertThat(response.getCurrentQuestionIndex()).isEqualTo(1);
        verify(values, never()).set(anyString(), any(), any(Duration.class));
        verifyNoInteractions(tts, ai);
    }

    @ParameterizedTest
    @CsvSource({"SOLO,999,202", "SOLO,101,999", "MULTI,999,202", "MULTI,101,999"})
    void 같은_세션의_면접이나_사용자_ID가_다르면_거절한다(InterviewMode mode, long interviewId, long userId) {
        mockExisting(existing(mode, interviewId, userId));
        StepVerifier.create(service.prepareInterview(request(mode)))
                .expectError(InterviewSessionAlreadyExistsException.class).verify(Duration.ofSeconds(2));
        verify(values, never()).set(anyString(), any(), any(Duration.class));
        verifyNoInteractions(tts, ai);
    }

    private void mockExisting(ChatInterviewSession existing) {
        when(values.setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(Mono.just(false));
        when(values.get("chat:interview:api-session")).thenReturn(Mono.just(existing));
    }

    private ChatInterviewSession existing(InterviewMode mode, Long interviewId, Long userId) {
        return ChatInterviewSession.builder().sessionId("api-session").interviewId(interviewId)
                .userId(userId).mode(mode).currentQuestionIndex(1).build();
    }

    private ChatInterviewPrepareRequest request(InterviewMode mode) {
        var request = new ChatInterviewPrepareRequest();
        request.setSessionId("api-session");
        request.setInterviewId(101L);
        request.setUserId(202L);
        request.setStatus(InterviewStatus.IN_PROGRESS);
        request.setMode(mode);
        request.setPersonality(InterviewType.FRIENDLY);
        request.setTone(InterviewTone.GENTLE);
        request.setMajor(InterviewMajor.BACKEND);
        request.setLevel(InterviewLevel.NORMAL);
        var question = new ChatInterviewPrepareRequest.Question();
        question.setId(303L);
        question.setQuestion("질문");
        question.setPersonaId(404L);
        request.setQuestions(List.of(question));
        return request;
    }
}
