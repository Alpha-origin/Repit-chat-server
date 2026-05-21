package com.person.repit.interview.controller;

import com.person.repit.interview.dto.request.InterviewQuestionRequest;
import com.person.repit.interview.dto.response.InterviewQuestionResponse;
import com.person.repit.interview.service.InterviewQuestionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/interviews/question")
public class InterviewQuestionController {

    private final InterviewQuestionService interviewQuestionService;

    @PostMapping
    public InterviewQuestionResponse createQuestion(@Valid @RequestBody InterviewQuestionRequest request) {
        return interviewQuestionService.createQuestion(request);
    }

    @GetMapping("/{questionId}")
    public InterviewQuestionResponse getQuestion(@PathVariable Long questionId) {
        return interviewQuestionService.getQuestion(questionId);
    }
}
