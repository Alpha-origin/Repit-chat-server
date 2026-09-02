package com.person.repit.interview.dto.request;

import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.type.InterviewExpertise;
import com.person.repit.interview.type.InterviewLevel;
import com.person.repit.interview.type.InterviewPersonality;
import com.person.repit.interview.type.InterviewTone;
import com.person.repit.interview.type.QuestionType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FollowQuestionAiRequest {

    private String sessionId;
    private Long interviewId;
    private Long userId;
    private Long personaId;
    private InterviewPersonality personality;
    private InterviewTone tone;
    private InterviewExpertise expertise;
    private InterviewLevel level;

    private Long questionId;
    private Long parentId;
    private QuestionType questionType;
    private String questionIntention;
    private String questionContent;
    private String expectedAnswer;

    private String answerContent;
    private Integer responseTime;

    public static FollowQuestionAiRequest of(
            String sessionId,
            Long interviewId,
            Long userId,
            Long personaId,
            InterviewPersonality personality,
            InterviewTone tone,
            InterviewExpertise expertise,
            InterviewLevel level,
            ChatQuestion question,
            String answerContent,
            Integer responseTime
    ) {
        return FollowQuestionAiRequest.builder()
                .sessionId(sessionId)
                .interviewId(interviewId)
                .userId(userId)
                .personaId(personaId)
                .personality(personality)
                .tone(tone)
                .expertise(expertise)
                .level(level)
                .questionId(question.getQuestionId())
                .parentId(question.getParentId())
                .questionType(question.getType())
                .questionIntention(question.getIntention())
                .questionContent(question.getContent())
                .expectedAnswer(question.getExpectedAnswer())
                .answerContent(answerContent)
                .responseTime(responseTime)
                .build();
    }
}
