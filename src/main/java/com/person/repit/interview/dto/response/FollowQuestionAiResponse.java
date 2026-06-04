package com.person.repit.interview.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class FollowQuestionAiResponse {

    private Boolean required;
    private String intention;
    private String content;

    public static FollowQuestionAiResponse notRequired() {
        FollowQuestionAiResponse response = new FollowQuestionAiResponse();
        response.required = false;
        return response;
    }
}