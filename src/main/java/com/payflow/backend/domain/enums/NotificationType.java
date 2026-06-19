package com.payflow.backend.domain.enums;

import lombok.Getter;

/**
 * Notification Type Enumeration
 */
@Getter
public enum NotificationType {
    ORDER_CREATED("Order Created", "Your order has been created"),
    ORDER_CONFIRMED("Order Confirmed", "Your order has been confirmed"),
    PAYMENT_SUCCESS("Payment Successful", "Payment has been processed"),
    PAYMENT_FAILED("Payment Failed", "Payment processing failed"),
    ORDER_SHIPPED("Order Shipped", "Your order has been shipped"),
    ORDER_DELIVERED("Order Delivered", "Your order has been delivered"),
    REFUND_INITIATED("Refund Initiated", "Your refund has been initiated"),
    REFUND_COMPLETED("Refund Completed", "Your refund has been completed"),
    INVENTORY_UPDATE("Inventory Update", "Product inventory has been updated"),
    PROMOTIONAL("Promotional", "Special offer for you");

    private final String displayName;
    private final String description;

    NotificationType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

}