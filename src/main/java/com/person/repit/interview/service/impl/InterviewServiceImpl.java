package com.person.repit.interview.service.impl;

import com.person.repit.interview.dto.request.InterviewRequest;
import com.person.repit.interview.dto.response.ChatInterviewResponse;
import com.person.repit.interview.entity.Interview;
import com.person.repit.interview.exception.InterviewNotFoundException;
import com.person.repit.interview.repository.InterviewRepository;
import com.person.repit.interview.service.InterviewService;
import com.person.repit.interview.type.InterviewStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {

    private final InterviewRepository interviewRepository;

    @Override
    @Transactional
    public ChatInterviewResponse createInterview(InterviewRequest request) {
        Interview interview = Interview.builder()
                .userId(request.getUserId())
                .personaId(request.getPersonaId())
                .sessionId(UUID.randomUUID().toString())
                .status(InterviewStatus.IN_PROGRESS)
                .build();

        interviewRepository.save(interview);
        return ChatInterviewResponse.from(interview);
    }

    @Override
    @Transactional(readOnly = true)
    public ChatInterviewResponse getInterview(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new InterviewNotFoundException("존재하지 않는 면접입니다."));

        return ChatInterviewResponse.from(interview);
    }

    @Override
    @Transactional(readOnly = true)
    public List<ChatInterviewResponse> getUserInterviews(Long userId) {
        return interviewRepository.findByUserId(userId)
                .stream()
                .map(ChatInterviewResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public ChatInterviewResponse completeInterview(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new InterviewNotFoundException("존재하지 않는 면접입니다."));

        interview.setStatus(InterviewStatus.COMPLETED);

        return ChatInterviewResponse.from(interview);
    }


    @Override
    @Transactional
    public ChatInterviewResponse quitInterview(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new InterviewNotFoundException("존재하지 않는 면접입니다."));

        interview.setStatus(InterviewStatus.ABANDONED);

        return ChatInterviewResponse.from(interview);
    }

}

