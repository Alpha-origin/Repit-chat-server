package com.person.repit.interview.exception;

import com.person.repit.common.exception.BusinessException;

public class ApiServerResponseException extends BusinessException {
    public ApiServerResponseException(String message) {
        super(message);
    }
}
