package com.payflow.backend.domain.enums;

/**
 * Card Brand Enumeration
 */
public enum CardBrand {
    VISA("Visa"),
    MASTERCARD("Mastercard"),
    AMEX("American Express"),
    DISCOVER("Discover"),
    UNKNOWN("Unknown");

    private final String displayName;

    CardBrand(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public static CardBrand fromBrand(String brand) {
        for (CardBrand cardBrand : CardBrand.values()) {
            if (cardBrand.name().equalsIgnoreCase(brand)) {
                return cardBrand;
            }
        }
        return UNKNOWN;
    }
}