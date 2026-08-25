package com.person.repit.interview.dto.response;

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
public class ChatInterviewAllResponse {
    private String sessionId;
    private Long interviewId;
    private Long userId;
    private List<InterviewPersona> interviewers;
    private InterviewStatus status;
    private int currentQuestionIndex;
    private LocalDateTime createdAt;
    private List<ChatInterviewQnAResponse> qnAResponses;

    public static ChatInterviewAllResponse from(ChatInterviewSession session) {
        Map<Long, ChatAnswer> answerByQuestionId = session.getAnswers()
                .stream()
                .collect(Collectors.toMap(
                        ChatAnswer::getQuestionId,
                        Function.identity()
                ));

        List<ChatInterviewQnAResponse> qnAResponses = session.getQuestions()
                .stream()
                .map(question -> ChatInterviewQnAResponse.of(
                        question,
                        answerByQuestionId.get(question.getQuestionId())
                ))
                .toList();

        return ChatInterviewAllResponse.builder()
                .sessionId(session.getSessionId())
                .interviewId(session.getInterviewId())
                .userId(session.getUserId())
                .interviewers(session.getInterviewers())
                .status(session.getStatus())
                .currentQuestionIndex(session.getCurrentQuestionIndex())
                .createdAt(session.getCreatedAt())
                .qnAResponses(qnAResponses)
                .build();
    }

    @Getter
    @Builder
    public static class ChatInterviewQnAResponse {
        private QuestionResponse question;
        private AnswerResponse answer;

        public static ChatInterviewQnAResponse of(
                ChatQuestion question,
                ChatAnswer answer
        ) {
            return ChatInterviewQnAResponse.builder()
                    .question(QuestionResponse.from(question))
                    .answer((answer == null) ? null : AnswerResponse.from(answer))
                    .build();
        }
    }

    @Getter
    @Builder
    public static class QuestionResponse {
        private Long questionId;
        private Long parentId;
        private QuestionType questionType;
        private String questionIntention;
        private String questionContent;
        private Long askedByPersonaId;
        private LocalDateTime questionCreatedAt;

        public static QuestionResponse from(ChatQuestion question) {
            return QuestionResponse.builder()
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
    public static class AnswerResponse {
        private Long interviewId;
        private Long questionId;
        private Long userId;
        private Integer responseTime;
        private String answerContent;
        private LocalDateTime answerCreatedAt;

        public static AnswerResponse from(ChatAnswer answer) {
            return AnswerResponse.builder()
                    .interviewId(answer.getInterviewId())
                    .questionId(answer.getQuestionId())
                    .userId(answer.getUserId())
                    .responseTime(answer.getResponseTime())
                    .answerContent(answer.getContent())
                    .answerCreatedAt(answer.getCreatedAt())
                    .build();
        }
    }

}
