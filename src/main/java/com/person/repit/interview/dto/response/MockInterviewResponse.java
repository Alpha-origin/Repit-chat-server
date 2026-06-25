package com.person.repit.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

import java.util.List;

@Getter
@JsonIgnoreProperties(ignoreUnknown = true)
public class MockInterviewResponse {

    private Boolean success;
    private Data data;

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Data {
        private Result result;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Result {
        private List<InterviewQuestion> interview;
    }

    @Getter
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class InterviewQuestion {

        private Integer id;
        private String category;
        private String question;

        @JsonProperty("expected_answer")
        private String expectedAnswer;
    }
}