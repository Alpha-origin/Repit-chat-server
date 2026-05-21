package com.person.repit.interview.controller;

import com.person.repit.interview.dto.request.InterviewAnswerRequest;
import com.person.repit.interview.dto.response.InterviewAnswerResponse;
import com.person.repit.interview.service.InterviewAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/interviews/answer")
public class InterviewAnswerController {

    private final InterviewAnswerService interviewAnswerService;

    @PostMapping
    public InterviewAnswerResponse createAnswer(InterviewAnswerRequest request) {
        return interviewAnswerService.createAnswer(request);
    }

    @GetMapping("/{answerId}")
    public InterviewAnswerResponse getAnswer(Long answerId) {
        return interviewAnswerService.getAnswer(answerId);
    }
}
