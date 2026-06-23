package com.person.repit.interview.dto.response;

import lombok.Getter;

import java.util.List;

@Getter
public class MockInterviewResponse {

    private String job_id;
    private String status;
    private Result result;

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