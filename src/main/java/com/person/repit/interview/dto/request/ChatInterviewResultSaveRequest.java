package com.person.repit.interview.dto.request;

import com.person.repit.interview.model.ChatAnswer;
import com.person.repit.interview.model.ChatInterviewSession;
import com.person.repit.interview.model.ChatQuestion;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.QuestionType;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ChatInterviewResultSaveRequest {

    private String sessionId;
    private Long interviewId;
    private Long userId;
    private InterviewStatus status;
    private List<QuestionResult> questions;
    private List<AnswerResult> answers;

    public static ChatInterviewResultSaveRequest from(ChatInterviewSession session) {
        return ChatInterviewResultSaveRequest.builder()
                .sessionId(session.getSessionId())
                .interviewId(session.getInterviewId())
                .userId(session.getUserId())
                .status(session.getStatus())
                .questions(session.getQuestions().stream().map(QuestionResult::from).toList())
                .answers(session.getAnswers().stream().map(AnswerResult::from).toList())
                .build();
    }

    @Getter
    @Builder
    public static class QuestionResult {

        private Long questionId;
        private Long parentId;
        private QuestionType type;
        private String intention;
        private String content;

        public static QuestionResult from(ChatQuestion question) {
            return QuestionResult.builder()
                    .questionId(question.getQuestionId())
                    .parentId(question.getParentId())
                    .type(question.getType())
                    .intention(question.getIntention())
                    .content(question.getContent())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class AnswerResult {

        private Long interviewId;
        private Long questionId;
        private Long userId;
        private Integer responseTime;
        private String content;

        public static AnswerResult from(ChatAnswer answer) {
            return AnswerResult.builder()
                    .interviewId(answer.getInterviewId())
                    .questionId(answer.getQuestionId())
                    .userId(answer.getUserId())
                    .responseTime(answer.getResponseTime())
                    .content(answer.getContent())
                    .build();
        }
    }
}
