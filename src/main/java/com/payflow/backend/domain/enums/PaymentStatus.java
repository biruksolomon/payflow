package com.payflow.backend.domain.enums;

import lombok.Getter;

/**
 * Payment Status Enumeration
 */
@Getter
public enum PaymentStatus {
    PENDING("Pending", "Payment pending processing"),
    SUCCESS("Successful", "Payment processed successfully"),
    FAILED("Failed", "Payment processing failed"),
    REFUNDED("Refunded", "Payment has been refunded"),
    PARTIALLY_REFUNDED("Partially Refunded", "Payment partially refunded");

    private final String displayName;
    private final String description;

    PaymentStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public static PaymentStatus fromString(String value) {
        for (PaymentStatus status : PaymentStatus.values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid payment status: " + value);
    }
}