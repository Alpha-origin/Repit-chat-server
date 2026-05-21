package com.person.repit.interview.service.impl;

import com.person.repit.interview.dto.request.InterviewAnswerRequest;
import com.person.repit.interview.dto.response.InterviewAnswerResponse;
import com.person.repit.interview.entity.InterviewAnswer;
import com.person.repit.interview.exception.InterviewAnswerNotFoundException;
import com.person.repit.interview.exception.InterviewNotFoundException;
import com.person.repit.interview.exception.InterviewQuestionNotFoundException;
import com.person.repit.interview.repository.InterviewAnswerRepository;
import com.person.repit.interview.repository.InterviewQuestionRepository;
import com.person.repit.interview.repository.InterviewRepository;
import com.person.repit.interview.service.InterviewAnswerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewAnswerServiceImpl implements InterviewAnswerService {

    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;
    private final InterviewAnswerRepository interviewAnswerRepository;

    @Override
    @Transactional
    InterviewAnswerResponse createAnswer(InterviewAnswerRequest request){
        interviewRepository.findById(request.getInterviewId())
                .orElseThrow(() -> new InterviewNotFoundException("존재하지 않는 면접입니다."));

        interviewQuestionRepository.findById(request.getQuestionId())
                .orElseThrow(() -> new InterviewQuestionNotFoundException("존재하지 않는 질문입니다."));

        InterviewAnswer answer = new InterviewAnswer().builder()
                .interviewId(request.getInterviewId())
                .questionId(request.getQuestionId())
                .userId(request.getUserId())
                .responseTime(request.getResponseTime())
                .content(request.getContent())
                .build();

        interviewAnswerRepository.save(answer);

        return InterviewAnswerResponse.from(answer);
    }

    @Override
    @Transactional(readOnly = true)
    InterviewAnswerResponse getAnswer(Long answerId){
        InterviewAnswer answer = interviewAnswerRepository.findById(answerId)
                .orElseThrow(() -> new InterviewAnswerNotFoundException("존재하지 않는 답변입니다."));

        return InterviewAnswerResponse.from(answer);
    }
}
