package com.payflow.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exception thrown when email/password combination is invalid
 */
public class InvalidCredentialsException extends AuthException {

    @Serial
    private static final long serialVersionUID = 1L;

    public InvalidCredentialsException() {
        super("Invalid email or password", "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
    }

    public InvalidCredentialsException(String message) {
        super(message, "INVALID_CREDENTIALS", HttpStatus.UNAUTHORIZED);
    }
}
