package com.payflow.backend.domain.enums;

/**
 * Fulfillment Status Enumeration
 */
public enum FulfillmentStatus {
    NOT_SHIPPED("Not Shipped"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    RETURNED("Returned");

    private final String displayName;

    FulfillmentStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}