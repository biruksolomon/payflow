package com.payflow.backend.exception;

import org.springframework.http.HttpStatus;

import java.io.Serial;

/**
 * Exception thrown when email verification fails
 */
public class EmailVerificationException extends AuthException {

    @Serial
    private static final long serialVersionUID = 1L;

    public EmailVerificationException(String message) {
        super(message, "EMAIL_VERIFICATION_FAILED", HttpStatus.BAD_REQUEST);
    }

    public static EmailVerificationException invalidToken() {
        return new EmailVerificationException("Invalid or expired verification token");
    }

    public static EmailVerificationException tokenExpired() {
        return new EmailVerificationException("Verification token has expired. Please request a new one");
    }

    public static EmailVerificationException alreadyVerified() {
        return new EmailVerificationException("Email is already verified");
    }
}
