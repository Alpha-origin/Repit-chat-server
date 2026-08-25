package com.person.repit.interview.dto.request;

import com.person.repit.interview.domain.ChatAnswer;
import com.person.repit.interview.domain.ChatInterviewSession;
import com.person.repit.interview.domain.ChatQuestion;
import com.person.repit.interview.domain.InterviewPersona;
import com.person.repit.interview.type.InterviewStatus;
import com.person.repit.interview.type.QuestionType;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Getter
@Builder
public class ChatInterviewAllRequest {

    private String sessionId;
    private Long interviewId;
    private Long userId;
    private List<InterviewPersona> interviewers;
    private InterviewStatus status;
    private LocalDateTime interviewCreatedAt;
    private List<ChatInterviewQnARequest> qnaRequests;

    public static ChatInterviewAllRequest from(ChatInterviewSession session) {
        Map<Long, ChatAnswer> answerByQuestionId = session.getAnswers()
                .stream()
                .collect(Collectors.toMap(
                        ChatAnswer::getQuestionId,
                        Function.identity()
                ));

        List<ChatInterviewQnARequest> qnaRequests = session.getQuestions()
                .stream()
                .map(question -> ChatInterviewQnARequest.of(
                        question,
                        answerByQuestionId.get(question.getQuestionId())
                ))
                .toList();

        return ChatInterviewAllRequest.builder()
                .sessionId(session.getSessionId())
                .interviewId(session.getInterviewId())
                .userId(session.getUserId())
                .interviewers(session.getInterviewers())
                .status(session.getStatus())
                .interviewCreatedAt(session.getCreatedAt())
                .qnaRequests(qnaRequests)
                .build();
    }

    @Getter
    @Builder
    public static class ChatInterviewQnARequest {

        private QuestionRequest question;
        private AnswerRequest answer;

        public static ChatInterviewQnARequest of(
                ChatQuestion question,
                ChatAnswer answer
        ) {
            return ChatInterviewQnARequest.builder()
                    .question(QuestionRequest.from(question))
                    .answer(answer == null ? null : AnswerRequest.from(answer))
                    .build();
        }
    }

    @Getter
    @Builder
    public static class QuestionRequest {

        private Long questionId;
        private Long parentId;
        private QuestionType questionType;
        private String questionIntention;
        private String questionContent;
        private Long askedByPersonaId;
        private LocalDateTime questionCreatedAt;

        public static QuestionRequest from(ChatQuestion question) {
            return QuestionRequest.builder()
                    .questionId(question.getQuestionId())
                    .parentId(question.getParentId())
                    .questionType(question.getType())
                    .questionIntention(question.getIntention())
                    .questionContent(question.getContent())
                    .askedByPersonaId(question.getAskedByPersonaId())
                    .questionCreatedAt(question.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class AnswerRequest {

        private Long questionId;
        private Integer responseTime;
        private String answerContent;
        private LocalDateTime answerCreatedAt;

        public static AnswerRequest from(ChatAnswer answer) {
            return AnswerRequest.builder()
                    .questionId(answer.getQuestionId())
                    .responseTime(answer.getResponseTime())
                    .answerContent(answer.getContent())
                    .answerCreatedAt(answer.getCreatedAt())
                    .build();
        }
    }
}
