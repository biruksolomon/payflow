package com.payflow.backend.domain.entity;

import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.PaymentMethod;
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


// ==================== PAYMENT ENTITY ====================
@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_order_id", columnList = "order_id"),
        @Index(name = "idx_payments_user_id", columnList = "user_id"),
        @Index(name = "idx_payments_payment_status", columnList = "payment_status"),
        @Index(name = "idx_payments_transaction_id", columnList = "transaction_id"),
        @Index(name = "idx_payments_created_at", columnList = "created_at"),
        @Index(name = "idx_payments_stripe_intent", columnList = "stripe_payment_intent_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Association
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // Payment Details
    @Column(nullable = false, length = 50)
    @Enumerated(EnumType.STRING)
    private PaymentMethod paymentMethod; // STRIPE, PAYPAL, CARD

    @Column(nullable = false, length = 50)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING; // PENDING, SUCCESS, FAILED, REFUNDED, PARTIALLY_REFUNDED

    // Amount Details
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(precision = 19, scale = 2)
    @Builder.Default
    private BigDecimal refundedAmount = BigDecimal.ZERO;

    @Column(length = 3)
    @Builder.Default
    @Enumerated(EnumType.STRING)
    private Currency currency = Currency.USD;

    // External Reference (from Stripe/PayPal)
    @Column(length = 255)
    private String stripePaymentIntentId;

    @Column(length = 255)
    private String stripeCustomerId;

    @Column(unique = true, length = 255)
    private String transactionId;

    // Card Information
    @Column(length = 4)
    private String cardLastFour;

    @Column(length = 50)
    private String cardBrand; // VISA, MASTERCARD, AMEX

    // Payment Metadata (JSONB in PostgreSQL)
    @Column(columnDefinition = "jsonb")
    private String paymentMetadata; // Store as JSON string

    // Timestamps
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime processedAt;

    private LocalDateTime refundedAt;

    // Error Handling
    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    @Column(length = 100)
    private String errorCode;

    @Column()
    @Builder.Default
    private Integer retryCount = 0;

    // Helper methods
    public boolean isSuccessful() {
        return paymentStatus == PaymentStatus.SUCCESS;
    }

    public boolean isFailed() {
        return paymentStatus == PaymentStatus.FAILED;
    }

    public boolean isPending() {
        return paymentStatus == PaymentStatus.PENDING;
    }

    public boolean isRefunded() {
        return paymentStatus == PaymentStatus.REFUNDED;
    }

    public BigDecimal getRemainingAmount() {
        return amount.subtract(refundedAmount != null ? refundedAmount : BigDecimal.ZERO);
    }

    public boolean canBeRefunded() {
        return isSuccessful() && getRemainingAmount().compareTo(BigDecimal.ZERO) > 0;
    }
}
