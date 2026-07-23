package com.person.repit.interview.exception;

import com.person.repit.common.exception.AlreadyUsingException;

public class InterviewSessionAlreadyExistsException extends AlreadyUsingException {
    public InterviewSessionAlreadyExistsException(String message) {
        super(message);
    }
}
