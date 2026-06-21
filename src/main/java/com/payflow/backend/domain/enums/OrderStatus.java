package com.payflow.backend.domain.enums;

import lombok.Getter;

/**
 * Order Status Enumeration
 */
@Getter
public enum OrderStatus {
    PENDING("Pending", "Order created, awaiting payment"),
    PROCESSING("Processing", "Payment received, preparing shipment"),
    SHIPPED("Shipped", "Order shipped to customer"),
    DELIVERED("Delivered", "Order delivered successfully"),
    CANCELLED("Cancelled", "Order has been cancelled");

    private final String displayName;
    private final String description;

    OrderStatus(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public static OrderStatus fromString(String value) {
        for (OrderStatus status : OrderStatus.values()) {
            if (status.name().equalsIgnoreCase(value)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Invalid order status: " + value);
    }

    public boolean canTransitionTo(OrderStatus nextStatus) {
        switch (this) {
            case PENDING:
                return nextStatus == PROCESSING || nextStatus == CANCELLED;
            case PROCESSING:
                return nextStatus == SHIPPED || nextStatus == CANCELLED;
            case SHIPPED:
                return nextStatus == DELIVERED;
            case DELIVERED:
            case CANCELLED:
                return false;
            default:
                return false;
        }
    }
}