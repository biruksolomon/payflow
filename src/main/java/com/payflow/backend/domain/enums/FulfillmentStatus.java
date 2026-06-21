package com.payflow.backend.domain.enums;

import lombok.Getter;

/**
 * Fulfillment Status Enumeration
 */
@Getter
public enum FulfillmentStatus {
    NOT_SHIPPED("Not Shipped"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    RETURNED("Returned");

    private final String displayName;

    FulfillmentStatus(String displayName) {
        this.displayName = displayName;
    }

}