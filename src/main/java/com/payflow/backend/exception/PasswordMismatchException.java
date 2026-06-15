package com.payflow.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exception thrown when password and password confirmation don't match
 */
public class PasswordMismatchException extends AuthException {

    @Serial
    private static final long serialVersionUID = 1L;

    public PasswordMismatchException() {
        super("Password and password confirmation do not match", "PASSWORD_MISMATCH", HttpStatus.BAD_REQUEST);
    }
}
