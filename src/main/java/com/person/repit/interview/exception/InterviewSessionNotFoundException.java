package com.person.repit.interview.exception;

import com.person.repit.common.exception.NotFoundException;

public class InterviewSessionNotFoundException extends NotFoundException {
    public InterviewSessionNotFoundException(String message) {
        super(message);
    }
}
