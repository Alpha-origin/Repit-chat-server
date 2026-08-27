package com.person.repit.interview.dto.response;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class FollowQuestionAiResponse {

    private Integer score;
    private Boolean required;
    private String intention;
    private String content;
    private String expectedAnswer;

    public static FollowQuestionAiResponse notRequired() {
        FollowQuestionAiResponse response = new FollowQuestionAiResponse();
        response.required = false;
        return response;
    }
}
