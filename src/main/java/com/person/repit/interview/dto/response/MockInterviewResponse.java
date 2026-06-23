package com.person.repit.interview.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class MockInterviewResponse {

    private Boolean success;
    private Data data;

    @Getter
    public static class Data {
        private Result result;
    }

    @Getter
    public static class Result {
        private List<InterviewQuestion> interview;
    }

    @Getter
    public static class InterviewQuestion {
        private Integer id;
        private String category;
        private String question;
        private String expected_answer;
    }
}