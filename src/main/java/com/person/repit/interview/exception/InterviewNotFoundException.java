package com.person.repit.interview.exception;

import com.person.repit.common.exception.NotFoundException;

public class InterviewNotFoundException extends NotFoundException {
    public InterviewNotFoundException(String message) {
        super(message);
    }
}
