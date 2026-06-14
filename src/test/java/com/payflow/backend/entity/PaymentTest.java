package com.payflow.backend.entity;

import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.domain.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class PaymentTest {

    @Test
    @DisplayName("Should identify successful payment")
    void shouldIdentifySuccessfulPayment() {

        Payment payment = Payment.builder()
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();

        assertTrue(payment.isSuccessful());

        assertFalse(payment.isFailed());
        assertFalse(payment.isPending());
        assertFalse(payment.isRefunded());
    }

    @Test
    @DisplayName("Should identify failed payment")
    void shouldIdentifyFailedPayment() {

        Payment payment = Payment.builder()
                .paymentStatus(PaymentStatus.FAILED)
                .build();

        assertTrue(payment.isFailed());

        assertFalse(payment.isSuccessful());
        assertFalse(payment.isPending());
        assertFalse(payment.isRefunded());
    }

    @Test
    @DisplayName("Should identify pending payment")
    void shouldIdentifyPendingPayment() {

        Payment payment = Payment.builder()
                .paymentStatus(PaymentStatus.PENDING)
                .build();

        assertTrue(payment.isPending());

        assertFalse(payment.isSuccessful());
        assertFalse(payment.isFailed());
        assertFalse(payment.isRefunded());
    }

    @Test
    @DisplayName("Should identify refunded payment")
    void shouldIdentifyRefundedPayment() {

        Payment payment = Payment.builder()
                .paymentStatus(PaymentStatus.REFUNDED)
                .build();

        assertTrue(payment.isRefunded());

        assertFalse(payment.isSuccessful());
        assertFalse(payment.isFailed());
        assertFalse(payment.isPending());
    }

    @Test
    @DisplayName("Should calculate remaining amount correctly")
    void shouldCalculateRemainingAmount() {

        Payment payment = Payment.builder()
                .amount(BigDecimal.valueOf(100))
                .refundedAmount(BigDecimal.valueOf(30))
                .build();

        assertEquals(
                BigDecimal.valueOf(70),
                payment.getRemainingAmount()
        );
    }

    @Test
    @DisplayName("Should treat null refunded amount as zero")
    void shouldHandleNullRefundedAmount() {

        Payment payment = Payment.builder()
                .amount(BigDecimal.valueOf(100))
                .refundedAmount(null)
                .build();

        assertEquals(
                BigDecimal.valueOf(100),
                payment.getRemainingAmount()
        );
    }

    @Test
    @DisplayName("Should allow refund for successful payment with remaining amount")
    void shouldAllowRefund() {

        Payment payment = Payment.builder()
                .paymentStatus(PaymentStatus.SUCCESS)
                .amount(BigDecimal.valueOf(100))
                .refundedAmount(BigDecimal.valueOf(20))
                .build();

        assertTrue(payment.canBeRefunded());
    }

    @Test
    @DisplayName("Should not allow refund for failed payment")
    void shouldNotAllowRefundForFailedPayment() {

        Payment payment = Payment.builder()
                .paymentStatus(PaymentStatus.FAILED)
                .amount(BigDecimal.valueOf(100))
                .refundedAmount(BigDecimal.ZERO)
                .build();

        assertFalse(payment.canBeRefunded());
    }

    @Test
    @DisplayName("Should not allow refund when fully refunded")
    void shouldNotAllowRefundWhenFullyRefunded() {

        Payment payment = Payment.builder()
                .paymentStatus(PaymentStatus.SUCCESS)
                .amount(BigDecimal.valueOf(100))
                .refundedAmount(BigDecimal.valueOf(100))
                .build();

        assertFalse(payment.canBeRefunded());
    }

    @Test
    @DisplayName("Should apply builder default values")
    void shouldApplyBuilderDefaults() {

        Payment payment = Payment.builder()
                .paymentMethod(PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(100))
                .build();

        assertEquals(
                PaymentStatus.PENDING,
                payment.getPaymentStatus()
        );

        assertEquals(
                BigDecimal.ZERO,
                payment.getRefundedAmount()
        );

        assertEquals(
                Currency.USD,
                payment.getCurrency()
        );

        assertEquals(
                Integer.valueOf(0),
                payment.getRetryCount()
        );
    }

    @Test
    @DisplayName("Should override builder default values")
    void shouldOverrideDefaults() {

        Payment payment = Payment.builder()
                .paymentMethod(PaymentMethod.PAYPAL)
                .paymentStatus(PaymentStatus.SUCCESS)
                .amount(BigDecimal.valueOf(500))
                .refundedAmount(BigDecimal.valueOf(50))
                .currency(Currency.EUR)
                .retryCount(3)
                .build();

        assertEquals(PaymentStatus.SUCCESS, payment.getPaymentStatus());

        assertEquals(
                BigDecimal.valueOf(50),
                payment.getRefundedAmount()
        );

        assertEquals(
                Currency.EUR,
                payment.getCurrency()
        );

        assertEquals(
                Integer.valueOf(3),
                payment.getRetryCount()
        );
    }

    @Test
    @DisplayName("Should create payment successfully")
    void shouldCreatePaymentSuccessfully() {

        Payment payment = Payment.builder()
                .paymentMethod(PaymentMethod.STRIPE)
                .amount(BigDecimal.valueOf(100))
                .transactionId("TXN-123")
                .build();

        assertNotNull(payment);

        assertEquals(
                PaymentMethod.STRIPE,
                payment.getPaymentMethod()
        );

        assertEquals(
                BigDecimal.valueOf(100),
                payment.getAmount()
        );

        assertEquals(
                "TXN-123",
                payment.getTransactionId()
        );
    }

}
