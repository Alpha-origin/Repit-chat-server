package com.person.repit.interview.exception;

import com.person.repit.common.exception.NotFoundException;

public class InterviewQuestionNotFoundException extends NotFoundException {
    public InterviewQuestionNotFoundException(String message) {
        super(message);
    }
}
