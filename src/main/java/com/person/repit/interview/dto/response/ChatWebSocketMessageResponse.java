package com.person.repit.interview.dto.response;

import com.person.repit.common.type.MessageType;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ChatWebSocketMessageResponse {

    private MessageType type;
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
        if (progress.getQuestion() == null) {
            return ChatWebSocketMessageResponse.builder()
                    .type(MessageType.END)
                    .message(progress.getMessage())
                    .build();
        }

        return ChatWebSocketMessageResponse.builder()
                .type(MessageType.QUESTION)
                .question(progress.getQuestion())
                .message(progress.getMessage())
                .build();
    }

    public static ChatWebSocketMessageResponse end(String message) {
        return ChatWebSocketMessageResponse.builder()
                .type(MessageType.END)
                .message(message)
                .build();
    }

    public static ChatWebSocketMessageResponse error(String message) {
        return ChatWebSocketMessageResponse.builder()
                .type(MessageType.ERROR)
                .message(message)
                .build();
    }
}