package com.person.repit.common.exception;

public class NoAuthorityException extends AuthenticationException {
    public NoAuthorityException(String message) {
        super(message);
    }
}
