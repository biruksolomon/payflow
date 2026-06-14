package com.payflow.backend.domain.entity;

import com.payflow.backend.domain.entity.OrderItem;
import com.payflow.backend.domain.enums.FulfillmentStatus;
import com.payflow.backend.domain.enums.OrderStatus;
import com.payflow.backend.domain.enums.PaymentStatus;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

// ==================== ORDER ENTITY ====================
@Entity
@Table(name = "orders", indexes = {
        @Index(name = "idx_orders_user_id", columnList = "user_id"),
        @Index(name = "idx_orders_order_status", columnList = "order_status"),
        @Index(name = "idx_orders_payment_status", columnList = "payment_status"),
        @Index(name = "idx_orders_created_at", columnList = "created_at"),
        @Index(name = "idx_orders_order_number", columnList = "order_number"),
        @Index(name = "idx_orders_user_created", columnList = "user_id,created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Order Identification
    @Column(unique = true, nullable = false, length = 50)
    private String orderNumber; // ORD-2024-001234

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Order Status
    @Column(nullable = false, length = 50)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private OrderStatus orderStatus = OrderStatus.PENDING; // PENDING, PROCESSING, SHIPPED, DELIVERED, CANCELLED

    @Column(nullable = false, length = 50)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING; // PENDING, SUCCESS, FAILED, REFUNDED

    @Column(nullable = false, length = 50)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private FulfillmentStatus fulfillmentStatus = FulfillmentStatus.NOT_SHIPPED; // NOT_SHIPPED, SHIPPED, DELIVERED, RETURNED

    // Pricing Details
    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal subtotal = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal taxAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal shippingCost = BigDecimal.ZERO;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal discountAmount = BigDecimal.ZERO;

    @Column(nullable = false, precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal totalPrice = BigDecimal.ZERO;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    // Shipping Information
    @Column(nullable = false, length = 255)
    private String shippingAddressStreet;

    @Column(nullable = false, length = 100)
    private String shippingAddressCity;

    @Column(length = 100)
    private String shippingAddressState;

    @Column(nullable = false, length = 20)
    private String shippingAddressPostalCode;

    @Column(nullable = false, length = 100)
    private String shippingAddressCountry;

    // Billing Information
    @Column(length = 255)
    private String billingAddressStreet;

    @Column(length = 100)
    private String billingAddressCity;

    @Column(length = 100)
    private String billingAddressState;

    @Column(length = 20)
    private String billingAddressPostalCode;

    @Column(length = 100)
    private String billingAddressCountry;

    // Shipping & Tracking
    @Column(length = 100)
    private String trackingNumber;

    private java.time.LocalDate estimatedDeliveryDate;

    private java.time.LocalDate actualDeliveryDate;

    // Notes
    @Column(columnDefinition = "TEXT")
    private String customerNotes;

    @Column(columnDefinition = "TEXT")
    private String adminNotes;

    // Relationships
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @Builder.Default
    private List<OrderItem> items = new ArrayList<>();

    // Tracking
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime shippedAt;

    private LocalDateTime deliveredAt;

    private LocalDateTime cancelledAt;

    // Helper methods
    public boolean isPending() {
        return OrderStatus.PENDING.equals(orderStatus);
    }

    public boolean isProcessing() {
        return OrderStatus.PROCESSING.equals(orderStatus);
    }

    public boolean isShipped() {
        return OrderStatus.SHIPPED.equals(orderStatus);
    }

    public boolean isDelivered() {
        return OrderStatus.DELIVERED.equals(orderStatus);
    }

    public boolean isCancelled() {
        return OrderStatus.CANCELLED.equals(orderStatus);
    }

    public boolean isPaymentSuccessful() {
        return PaymentStatus.SUCCESS.equals(paymentStatus);
    }

    public int getItemCount() {
        return items != null ? items.size() : 0;
    }

    public void addItem(OrderItem item) {
        if (items == null) {
            items = new ArrayList<>();
        }
        items.add(item);
        item.setOrder(this);
    }
}