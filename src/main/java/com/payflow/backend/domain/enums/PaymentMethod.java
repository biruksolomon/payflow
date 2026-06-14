package com.payflow.backend.domain.enums;

/**
 * Payment Method Enumeration
 */
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

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}
