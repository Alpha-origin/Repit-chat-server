package com.person.repit.interview.controller;

import com.person.repit.interview.dto.request.InterviewRequest;
import com.person.repit.interview.dto.response.InterviewResponse;
import com.person.repit.interview.service.InterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/interviews")
public class InterviewController {

    private final InterviewService interviewService;

    @PostMapping
    public InterviewResponse createInterview(@Valid @RequestBody InterviewRequest request) {
        return interviewService.createInterview(request);
    }

    @GetMapping("/{interviewId}")
    public InterviewResponse getInterview(@PathVariable Long interviewId) {
        return interviewService.getInterview(interviewId);
    }

    @GetMapping("/{userId}")
    public List<InterviewResponse> getUserInterviews(@PathVariable Long userId) {
        return interviewService.getUserInterviews(userId);
    }

    @PutMapping("/{interviewId}/complete")
    public InterviewResponse completeInterview(@PathVariable Long interviewId) {
        return interviewService.completeInterview(interviewId);
    }

    @PutMapping("/{interviewId}/quit")
    public InterviewResponse quitInterview(@PathVariable Long interviewId) {
        return interviewService.quitInterview(interviewId);
    }
}
