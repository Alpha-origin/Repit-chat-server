package com.person.repit.interview.service.impl;

import com.person.repit.interview.dto.response.QuestionResponse;
import com.person.repit.interview.entity.Interview;
import com.person.repit.interview.service.InterviewService;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.QuestionType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Random;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterviewServiceImpl implements InterviewService {
    @Override
    public QuestionResponse startInterview(StartInterviewRequest request) {
        Interview interview = new Interview();

        interview.setInterviewId(1L);
        interview.setPersonaId(request.getPersonaId());
        interview.setStatus(InterviewStatus.IN_PROGRESS);

        log.info("면접 생성 완료 : personaId={}", request.getPersonaId());

        List<String> questions = List.of(
                "자기소개 해주세요.",
                "지원 동기를 말씀해주세요.",
                "협업 경험을 설명해주세요."
        );

        Random random = new Random();

        String selectQuestion = questions.get(random.nextInt(questions.size()));

        log.info("첫 질문 선택 완료 : {}", selectQuestion);

        return QuestionResponse
                .builder()
                .questionId(1l)
                .questionType(
                        QuestionType.ORIGINAL
                )
                .content(selectQuestion)
                .build();
    }
}
