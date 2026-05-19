package com.person.repit.interview.service;

import com.person.repit.interview.dto.response.QuestionResponse;

public interface InterviewService {
    QuestionResponse startInterview(StartInterviewRequest request);
}
