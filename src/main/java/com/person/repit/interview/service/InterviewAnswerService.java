package com.person.repit.interview.service;

import com.person.repit.interview.dto.request.InterviewAnswerRequest;
import com.person.repit.interview.dto.response.InterviewAnswerResponse;

public interface InterviewAnswerService {
    InterviewAnswerResponse createAnswer(InterviewAnswerRequest request);
    InterviewAnswerResponse getAnswer(Long answerId);
}
