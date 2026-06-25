package com.person.repit.interview.controller;

import com.person.repit.interview.dto.request.ChatAnswerRequest;
import com.person.repit.interview.dto.request.ChatInterviewPrepareRequest;
import com.person.repit.interview.dto.response.ChatInterviewResponse;
import com.person.repit.interview.dto.response.ChatProgressResponse;
import com.person.repit.interview.dto.response.ChatQuestionResponse;
import com.person.repit.interview.service.ChatInterviewService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat/interviews")
@RequiredArgsConstructor
public class ChatInterviewController {
    private final ChatInterviewService chatInterviewService;

    @PostMapping
    public ChatInterviewResponse prepareInterview(
            @Valid @RequestBody ChatInterviewPrepareRequest request,
            @RequestHeader("Authorization") String authorization
    ) {
        return chatInterviewService.prepareInterview(request, authorization);
    }

    @GetMapping("/{sessionId}/question")
    public ChatQuestionResponse getCurrentQuestion(@PathVariable String sessionId) {
        return chatInterviewService.getCurrentQuestion(sessionId);
    }

    @PostMapping("/{sessionId}/answers")
    public ChatProgressResponse submitAnswer(
            @PathVariable String sessionId,
            @Valid @RequestBody ChatAnswerRequest request
    ) {
        return chatInterviewService.submitAnswer(sessionId, request);
    }

    @PostMapping("/{sessionId}/complete")
    public ChatProgressResponse completeInterview(@PathVariable String sessionId) {
        return chatInterviewService.completeInterview(sessionId);
    }

    @PostMapping("/{sessionId}/quit")
    public ChatProgressResponse quitInterview(@PathVariable String sessionId) {
        return chatInterviewService.quitInterview(sessionId);
    }
}
