package com.person.repit.interview.service;

import com.person.repit.interview.dto.response.InterviewQuestionResponse;

public interface InterviewService {
    InterviewQuestionResponse startInterview(StartInterviewRequest request);
}
