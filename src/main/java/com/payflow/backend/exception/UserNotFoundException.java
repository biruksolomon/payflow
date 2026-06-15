package com.payflow.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exception thrown when a user is not found in the database
 */
public class UserNotFoundException extends AuthException {

    @Serial
    private static final long serialVersionUID = 1L;

    public UserNotFoundException(String email) {
        super("User not found with email: " + email, "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }

    public UserNotFoundException(Long userId) {
        super("User not found with id: " + userId, "USER_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
