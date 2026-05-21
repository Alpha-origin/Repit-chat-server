package com.person.repit.interview.service;


import com.person.repit.interview.dto.request.InterviewRequest;
import com.person.repit.interview.dto.response.InterviewResponse;

import java.util.List;

public interface InterviewService {
    InterviewResponse createInterview(InterviewRequest request);
    InterviewResponse getInterview(Long interviewId);
    List<InterviewResponse> getUserInterviews(Long userId);
    InterviewResponse completeInterview(Long interviewId);
    InterviewResponse quitInterview(Long interviewId);
}
