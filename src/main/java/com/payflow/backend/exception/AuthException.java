package com.payflow.backend.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.io.Serial;

/**
 * Base exception for authentication-related errors
 */
@Getter
@ResponseStatus(HttpStatus.UNAUTHORIZED)
public class AuthException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;
    private final String errorCode;
    private final HttpStatus httpStatus;

    public AuthException(String message) {
        this(message, "AUTH_ERROR", HttpStatus.UNAUTHORIZED);
    }

    public AuthException(String message, String errorCode) {
        this(message, errorCode, HttpStatus.UNAUTHORIZED);
    }

    public AuthException(String message, String errorCode, HttpStatus httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

}
