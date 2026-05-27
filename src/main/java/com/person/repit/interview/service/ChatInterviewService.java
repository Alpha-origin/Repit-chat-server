package com.person.repit.interview.service;

import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.dto.response.ChatInterviewResponse;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.dto.response.ChatQuestionResponse;

public interface ChatInterviewService {
    ChatInterviewResponse prepareInterview(ChatInterviewPrepareRequest request);

    ChatQuestionResponse getCurrentQuestion(String sessionId);

    ChatProgressResponse submitAnswer(String sessionId, ChatAnswerRequest request);

    ChatProgressResponse completeInterview(String sessionId);

    ChatProgressResponse quitInterview(String sessionId);
}
