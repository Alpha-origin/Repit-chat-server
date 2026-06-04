package com.person.repit.interview.service;

import com.person.repit.interview.dto.request.FollowQuestionAiRequest;
import com.person.repit.interview.dto.response.FollowQuestionAiResponse;

public interface AiQuestionClient {

    FollowQuestionAiResponse decideFollowQuestion(FollowQuestionAiRequest request);
}
