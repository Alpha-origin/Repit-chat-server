package com.person.repit.interview.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class MockInterviewResponse {

    @JsonProperty("job_id")
    private String jobId;

    private String status;

    private Result result;

    @Getter
    public static class Result {

        private List<InterviewQuestion> interview;
    }

    @Getter
    public static class InterviewQuestion {

        private Long id;

        private String category;

        private String question;

        @JsonProperty("expected_answer")
        private String expectedAnswer;

        @JsonProperty("based_on")
        private List<String> basedOn;
    }
}