package com.person.repit.interview.service;


import com.person.repit.interview.dto.request.InterviewRequest;
import com.person.repit.interview.dto.response.ChatInterviewResponse;

import java.util.List;

public interface InterviewService {
    ChatInterviewResponse createInterview(InterviewRequest request);
    ChatInterviewResponse getInterview(Long interviewId);
    List<ChatInterviewResponse> getUserInterviews(Long userId);
    ChatInterviewResponse completeInterview(Long interviewId);
    ChatInterviewResponse quitInterview(Long interviewId);
}
