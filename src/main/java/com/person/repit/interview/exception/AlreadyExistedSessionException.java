package com.person.repit.interview.exception;

import com.person.repit.common.exception.AlreadyUsingException;

public class AlreadyExistedSessionException extends AlreadyUsingException {
    public AlreadyExistedSessionException(String message) {
        super(message);
    }
}
