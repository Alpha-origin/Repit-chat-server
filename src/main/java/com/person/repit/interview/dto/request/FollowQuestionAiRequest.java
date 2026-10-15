package com.person.repit.interview.dto.request;

import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.domain.CompanyContext;
import com.person.repit.interview.type.InterviewLevel;
import com.person.repit.interview.type.InterviewMajor;
import com.person.repit.interview.type.InterviewRole;
import com.person.repit.interview.type.InterviewType;
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
    private InterviewType personality;
    private InterviewTone tone;
    private InterviewMajor major;
    private InterviewLevel level;
    private InterviewRole role;
    private String companyName;
    private String companyBrief;

    private Long questionId;
    private Long parentId;
    private QuestionType questionType;
    private String questionCategory;
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
            InterviewType personality,
            InterviewTone tone,
            InterviewMajor major,
            InterviewLevel level,
            InterviewRole role,
            CompanyContext company,
            ChatQuestion question,
            String answerContent,
            Integer responseTime
    ) {
        boolean legacy = question.getCategory() == null;

        return FollowQuestionAiRequest.builder()
                .sessionId(sessionId)
                .interviewId(interviewId)
                .userId(userId)
                .personaId(personaId)
                .personality(personality)
                .tone(tone)
                .major(major)
                .level(level)
                .role(role)
                .companyName(company == null ? null : company.getDisplayName())
                .companyBrief(company == null ? null : company.getInterviewerBrief())
                .questionId(question.getQuestionId())
                .parentId(question.getParentId())
                .questionType(question.getType())
                .questionCategory(legacy ? question.getIntention() : question.getCategory())
                .questionIntention(legacy ? null : question.getIntention())
                .questionContent(question.getContent())
                .expectedAnswer(question.getExpectedAnswer())
                .answerContent(answerContent)
                .responseTime(responseTime)
                .build();
    }
}
