package com.person.repit.interview.domain;

import com.person.repit.interview.type.InterviewLevel;
import com.person.repit.interview.type.InterviewStatus;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChatInterviewSession {
    private String sessionId;
    private Long interviewId;
    private Long userId;
    private InterviewLevel level;

    @Builder.Default
    private List<InterviewPersona> interviewers = new ArrayList<>();

    @Builder.Default
    private InterviewStatus status = InterviewStatus.IN_PROGRESS;

    @Builder.Default
    private List<ChatQuestion> questions = new ArrayList<>();

    @Builder.Default
    private List<ChatAnswer> answers = new ArrayList<>();

    @Builder.Default
    private int currentQuestionIndex = 0;

    private LocalDateTime createdAt;

    public ChatQuestion getCurrentQuestion() {
        if ((currentQuestionIndex < 0) || (currentQuestionIndex >= questions.size())) {
            return null;
        }
        return questions.get(currentQuestionIndex);
    }

    public void moveNextQuestion() {
        if ((questions == null) || (questions.isEmpty())) {
            currentQuestionIndex = -1;
            return;
        }

        int nextIndex = currentQuestionIndex + 1;

        if (nextIndex >= questions.size()) {
            currentQuestionIndex = -1;
        } else {
            currentQuestionIndex = nextIndex;
        }
    }
}
