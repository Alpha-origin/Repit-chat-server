package com.person.repit.interview.service;

import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.dto.response.ChatInterviewAllResponse;
import com.person.repit.interview.dto.response.ChatInterviewResponse;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.dto.response.ChatQuestionResponse;
import reactor.core.publisher.Mono;

public interface ChatInterviewService {
    Mono<ChatInterviewResponse> prepareInterview(ChatInterviewPrepareRequest request);

    Mono<ChatInterviewAllResponse> getInterview(String sessionId);

    Mono<ChatQuestionResponse> getCurrentQuestion(String sessionId);

    Mono<ChatProgressResponse> submitAnswer(String sessionId, ChatAnswerRequest request);

    Mono<ChatProgressResponse> completeInterview(String sessionId);

    Mono<ChatProgressResponse> quitInterview(String sessionId);
}
