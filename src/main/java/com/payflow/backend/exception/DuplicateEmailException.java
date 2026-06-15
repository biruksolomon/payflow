package com.payflow.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exception thrown when attempting to register with an email that already exists
 */
public class DuplicateEmailException extends AuthException {

    @Serial
    private static final long serialVersionUID = 1L;

    public DuplicateEmailException(String email) {
        super("Email " + email + " is already registered", "DUPLICATE_EMAIL", HttpStatus.CONFLICT);
    }
}
