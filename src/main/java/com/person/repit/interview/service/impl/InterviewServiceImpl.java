package com.person.repit.interview.service.impl;

import com.person.repit.interview.dto.request.InterviewRequest;
import com.person.repit.interview.dto.response.InterviewResponse;
import com.person.repit.interview.entity.Interview;
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
    public InterviewResponse createInterview(InterviewRequest request) {
        Interview interview = Interview.builder()
                .userId(request.getUserId())
                .personaId(request.getPersonaId())
                .sessionId(UUID.randomUUID().toString())
                .status(InterviewStatus.IN_PROGRESS)
                .build();

        interviewRepository.save(interview);
        return InterviewResponse.from(interview);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewResponse getInterview(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 면접입니다."));

        return InterviewResponse.from(interview);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InterviewResponse> getUserInterviews(Long userId) {
        return interviewRepository.findByUserId(userId)
                .stream()
                .map(InterviewResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public InterviewResponse completeInterview(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 면접입니다."));

        interview.setStatus(InterviewStatus.COMPLETED);

        return InterviewResponse.from(interview);
    }


    @Override
    @Transactional
    public InterviewResponse quitInterview(Long interviewId) {
        Interview interview = interviewRepository.findById(interviewId)
                .orElseThrow(() -> new IllegalArgumentException("존재하지 않는 면접입니다."));

        interview.setStatus(InterviewStatus.ABANDONED);

        return InterviewResponse.from(interview);
    }

}

