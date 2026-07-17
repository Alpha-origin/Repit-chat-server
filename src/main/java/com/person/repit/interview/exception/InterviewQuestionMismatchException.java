package com.person.repit.interview.exception;

import com.person.repit.common.exception.InvalidRequestException;

public class InterviewQuestionMismatchException extends InvalidRequestException {
    public InterviewQuestionMismatchException(String message) {
        super(message);
    }
}
