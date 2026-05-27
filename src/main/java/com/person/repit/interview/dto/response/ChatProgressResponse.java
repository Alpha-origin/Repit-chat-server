package com.person.repit.interview.dto.response;

import com.person.repit.interview.model.ChatQuestion;
import com.person.repit.interview.type.InterviewStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatProgressResponse {

    private InterviewStatus status;
    private ChatQuestionResponse question;
    private String message;

    public static ChatProgressResponse next(ChatQuestion question) {
        return ChatProgressResponse.builder()
                .status(InterviewStatus.IN_PROGRESS)
                .question(ChatQuestionResponse.from(question))
                .message("다음 질문입니다.")
                .build();
    }

    public static ChatProgressResponse end() {
        return ChatProgressResponse.builder()
                .status(InterviewStatus.COMPLETED)
                .message("면접이 종료되었습니다.")
                .build();
    }

    public static ChatProgressResponse quit() {
        return ChatProgressResponse.builder()
                .status(InterviewStatus.ABANDONED)
                .message("면접이 중단되었습니다.")
                .build();
    }
}
