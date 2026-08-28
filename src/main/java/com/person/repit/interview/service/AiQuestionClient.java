package com.person.repit.interview.service;

import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;
import reactor.core.publisher.Mono;

public interface AiQuestionClient {
    Mono<FollowQuestionAiResponse> decideFollowQuestionReactive(FollowQuestionAiRequest request);
}
