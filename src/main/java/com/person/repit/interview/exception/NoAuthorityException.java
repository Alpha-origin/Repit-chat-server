package com.person.repit.interview.exception;

public class NoAuthorityException extends AuthenticationException {
    public NoAuthorityException(String message) {
        super(message);
    }
}
