package com.person.repit.interview.service;

import com.person.repit.interview.dto.request.InterviewQuestionRequest;
import com.person.repit.interview.dto.response.ChatQuestionResponse;

public interface InterviewQuestionService {
    ChatQuestionResponse createQuestion(InterviewQuestionRequest request);
    ChatQuestionResponse getQuestion(Long questionId);
}
