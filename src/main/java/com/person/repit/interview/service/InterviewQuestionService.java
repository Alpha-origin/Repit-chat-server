package com.person.repit.interview.service;

import com.person.repit.interview.dto.request.InterviewQuestionRequest;
import com.person.repit.interview.dto.response.InterviewQuestionResponse;

public interface InterviewQuestionService {
    InterviewQuestionResponse createQuestion(InterviewQuestionRequest request);
}
