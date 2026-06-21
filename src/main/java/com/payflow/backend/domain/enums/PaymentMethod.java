package com.payflow.backend.domain.enums;

import lombok.Getter;

/**
 * Payment Method Enumeration
 */
@Getter
public enum PaymentMethod {
    STRIPE("Stripe", "Stripe Payment Gateway"),
    PAYPAL("PayPal", "PayPal Payment Gateway"),
    CREDIT_CARD("Credit Card", "Direct credit card payment"),
    DEBIT_CARD("Debit Card", "Direct debit card payment");

    private final String displayName;
    private final String description;

    PaymentMethod(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}
