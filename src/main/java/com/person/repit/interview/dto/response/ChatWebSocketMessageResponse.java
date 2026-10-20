package com.person.repit.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.person.repit.common.type.MessageType;
import com.person.repit.interview.type.InterviewStatus;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatWebSocketMessageResponse {

    private MessageType type;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private InterviewStatus status;
    private ChatQuestionResponse question;
    private String message;

    public static ChatWebSocketMessageResponse question(ChatQuestionResponse question) {
        return ChatWebSocketMessageResponse.builder()
                .type(MessageType.QUESTION)
                .question(question)
                .message("질문입니다.")
                .build();
    }

    public static ChatWebSocketMessageResponse progress(ChatProgressResponse progress) {
        return ChatWebSocketMessageResponse.builder()
                .type(progress.getQuestion() == null ? MessageType.END : MessageType.QUESTION)
                .status(progress.getStatus())
                .question(progress.getQuestion())
                .message(progress.getMessage())
                .build();
    }

    public static ChatWebSocketMessageResponse error(String message) {
        return ChatWebSocketMessageResponse.builder()
                .type(MessageType.ERROR)
                .message(message)
                .build();
    }
}
