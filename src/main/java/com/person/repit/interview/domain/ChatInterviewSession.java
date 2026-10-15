package com.person.repit.interview.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.person.repit.interview.type.*;
import com.person.repit.tts.domain.TtsProfile;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class ChatInterviewSession {
    private String sessionId;
    private Long interviewId;
    private Long userId;
    private InterviewType personality;
    private InterviewTone tone;
    private InterviewMajor major;
    private InterviewLevel level;
    private InterviewMode mode;
    private CompanyContext company;
    private Map<Long, PersonaSnapshot> personas;
    private Map<Long, TtsProfile> ttsProfiles;
    private String ttsProfileVersion;

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
