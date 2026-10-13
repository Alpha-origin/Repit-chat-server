package com.person.repit.tts.controller;

import com.person.repit.tts.dto.InterviewAudioResponse;
import com.person.repit.tts.service.InterviewAudioService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/chat/interviews")
@RequiredArgsConstructor
public class InterviewAudioController {
    private final InterviewAudioService audioService;

    @GetMapping("/{sessionId}/questions/{questionId}/audio")
    public Mono<InterviewAudioResponse> getAudio(
            @PathVariable String sessionId,
            @PathVariable Long questionId,
            @RequestParam(required = false) Integer wait
    ) {
        return audioService.getAudio(sessionId, questionId, wait);
    }
}
