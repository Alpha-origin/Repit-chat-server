package com.person.repit.interview.service.impl;

import com.person.repit.interview.dto.request.InterviewQuestionRequest;
import com.person.repit.interview.dto.response.InterviewQuestionResponse;
import com.person.repit.interview.entity.InterviewQuestion;
import com.person.repit.interview.exception.InterviewNotFoundException;
import com.person.repit.interview.exception.InterviewQuestionNotFoundException;
import com.person.repit.interview.repository.InterviewQuestionRepository;
import com.person.repit.interview.repository.InterviewRepository;
import com.person.repit.interview.service.InterviewQuestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InterviewQuestionServiceImpl implements InterviewQuestionService {

    private final InterviewRepository interviewRepository;
    private final InterviewQuestionRepository interviewQuestionRepository;

    @Override
    @Transactional(readOnly = true)
    public InterviewQuestionResponse createQuestion(InterviewQuestionRequest request) {
        interviewRepository.findById(request.getInterviewId())
                .orElseThrow(() -> new InterviewNotFoundException("존재하지 않는 면접입니다."));

        InterviewQuestion question = InterviewQuestion.builder()
                .interviewId(request.getInterviewId())
                .parentId(request.getParentId())
                .type(request.getType())
                .intention(request.getIntention())
                .content(request.getContent())
                .build();

        interviewQuestionRepository.save(question);

        return InterviewQuestionResponse.from(question);
    }

    @Override
    @Transactional(readOnly = true)
    public InterviewQuestionResponse getQuestion(Long questionId){
        InterviewQuestion question = interviewQuestionRepository.findById(questionId)
                .orElseThrow(() -> new InterviewQuestionNotFoundException("해당 질문은 존재하지 않습니다."));

        return InterviewQuestionResponse.from(question);
    }
}
