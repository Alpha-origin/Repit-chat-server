package com.person.repit.interview.exception;

import com.person.repit.common.exception.NotFoundException;

public class InterviewAnswerNotFoundException extends NotFoundException {
    public InterviewAnswerNotFoundException(String message) {
        super(message);
    }
}
