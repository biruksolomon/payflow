package com.payflow.backend.service;

import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.domain.enums.PaymentStatus;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.OrderRepository;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * PaymentService — initiates, records, and reconciles payments.
 *
 * This service is gateway-agnostic.  Real gateway calls (Stripe, PayPal, etc.)
 * should be performed in a thin adapter layer before calling these methods.
 * The adapter resolves the external transaction/intent ID and then calls
 * {@link #recordSuccessfulPayment} or {@link #recordFailedPayment}.
 *
 * Supported flows:
 *  1. initiatePayment       — creates a PENDING Payment row linked to an Order.
 *  2. recordSuccessfulPayment — marks the payment SUCCESS, triggers OrderService.confirmOrder().
 *  3. recordFailedPayment   — marks the payment FAILED, fires PAYMENT_FAILED notification.
 *  4. initiateRefund        — partial or full refund; marks order paymentStatus = REFUNDED.
 *  5. completeRefund        — called by webhook after the gateway confirms the refund.
 *  6. getPaymentsForUser    — user's own payment history.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final OrderService orderService;
    private final NotificationService notificationService;

    // ─────────────────────────────────────────────────────────────
    // INITIATE
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a PENDING Payment row for an order that is about to be charged.
     *
     * @param orderId          the order being paid
     * @param userId           authenticated user making the payment
     * @param paymentMethod    STRIPE, PAYPAL, CREDIT_CARD, etc.
     * @param stripeIntentId   optional — Stripe PaymentIntent ID (null for non-Stripe)
     * @param cardLastFour     optional last four digits of the card
     * @param cardBrand        optional card brand string (VISA, MC, etc.)
     * @return the persisted PENDING Payment
     */
    @Transactional
    public Payment initiatePayment(
            Long orderId,
            Long userId,
            PaymentMethod paymentMethod,
            String stripeIntentId,
            String cardLastFour,
            String cardBrand) {

        Order order = findOrder(orderId);
        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!order.getUser().getId().equals(userId)) {
            throw new AuthException("Not authorized to pay for this order", "FORBIDDEN");
        }

        // Prevent duplicate payments
        paymentRepository.findByOrderId(orderId).ifPresent(existing -> {
            if (existing.getPaymentStatus() == PaymentStatus.SUCCESS) {
                throw new AuthException(
                        "Order is already paid: " + orderId, "ALREADY_PAID");
            }
        });

        Payment payment = Payment.builder()
                .order(order)
                .user(user)
                .paymentMethod(paymentMethod)
                .paymentStatus(PaymentStatus.PENDING)
                .amount(order.getTotalPrice())
                .currency(Currency.USD)
                .stripePaymentIntentId(stripeIntentId)
                .transactionId(generateTransactionId())
                .cardLastFour(cardLastFour)
                .cardBrand(cardBrand)
                .build();

        Payment saved = paymentRepository.save(payment);
        log.info("Payment initiated — paymentId={} orderId={} amount={} method={}",
                saved.getId(), orderId, order.getTotalPrice(), paymentMethod);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // RECORD SUCCESS (called by gateway webhook / response handler)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Payment recordSuccessfulPayment(Long paymentId) {
        Payment payment = findPayment(paymentId);

        if (payment.getPaymentStatus() == PaymentStatus.SUCCESS) {
            log.warn("Payment already marked as SUCCESS — paymentId={}", paymentId);
            return payment;
        }

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setProcessedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);

        // Transition order to PROCESSING and deduct inventory
        orderService.confirmOrder(payment.getOrder().getId());

        notificationService.sendPaymentSuccessNotification(saved);
        log.info("Payment succeeded — paymentId={} orderId={}", paymentId, payment.getOrder().getId());
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // RECORD FAILURE
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Payment recordFailedPayment(Long paymentId, String errorCode, String errorMessage) {
        Payment payment = findPayment(paymentId);

        payment.setPaymentStatus(PaymentStatus.FAILED);
        payment.setErrorCode(errorCode);
        payment.setErrorMessage(errorMessage);
        payment.setRetryCount(payment.getRetryCount() + 1);

        Payment saved = paymentRepository.save(payment);
        notificationService.sendPaymentFailedNotification(saved);
        log.warn("Payment failed — paymentId={} orderId={} errorCode={} errorMessage={}",
                paymentId, payment.getOrder().getId(), errorCode, errorMessage);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // REFUND
    // ─────────────────────────────────────────────────────────────

    /**
     * Marks a refund as initiated (PENDING state before the gateway confirms it).
     * Does not call the external gateway — the caller must do that before invoking this method.
     *
     * @param orderId     the order being refunded
     * @param userId      requestor (must own the order unless admin)
     * @param isAdmin     whether the requestor is an admin
     * @param refundAmount amount to refund (null = full remaining amount)
     */
    @Transactional
    public Payment initiateRefund(Long orderId, Long userId, boolean isAdmin, BigDecimal refundAmount) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AuthException("Payment not found for order: " + orderId, "PAYMENT_NOT_FOUND"));

        if (!isAdmin && !payment.getUser().getId().equals(userId)) {
            throw new AuthException("Not authorized to refund this payment", "FORBIDDEN");
        }

        if (!payment.canBeRefunded()) {
            throw new AuthException(
                    "Payment cannot be refunded — status: " + payment.getPaymentStatus(),
                    "REFUND_NOT_ALLOWED");
        }

        BigDecimal amount = (refundAmount != null && refundAmount.compareTo(BigDecimal.ZERO) > 0)
                ? refundAmount
                : payment.getRemainingAmount();

        if (amount.compareTo(payment.getRemainingAmount()) > 0) {
            throw new AuthException(
                    "Refund amount exceeds remaining payment: " + payment.getRemainingAmount(),
                    "REFUND_EXCEEDS_AMOUNT");
        }

        payment.setRefundedAmount(
                (payment.getRefundedAmount() != null ? payment.getRefundedAmount() : BigDecimal.ZERO)
                        .add(amount));

        boolean fullRefund = payment.getRefundedAmount().compareTo(payment.getAmount()) >= 0;
        payment.setPaymentStatus(fullRefund ? PaymentStatus.REFUNDED : PaymentStatus.PARTIALLY_REFUNDED);

        Payment saved = paymentRepository.save(payment);
        notificationService.sendRefundInitiatedNotification(saved);

        log.info("Refund initiated — paymentId={} amount={} fullRefund={}",
                saved.getId(), amount, fullRefund);
        return saved;
    }

    /**
     * Called by the gateway webhook once the refund is confirmed on their end.
     */
    @Transactional
    public Payment completeRefund(Long paymentId) {
        Payment payment = findPayment(paymentId);
        payment.setRefundedAt(LocalDateTime.now());
        Payment saved = paymentRepository.save(payment);
        notificationService.sendRefundCompletedNotification(saved);
        log.info("Refund completed — paymentId={}", paymentId);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Payment getPaymentByOrderId(Long orderId, Long userId, boolean isAdmin) {
        Payment payment = paymentRepository.findByOrderId(orderId)
                .orElseThrow(() -> new AuthException(
                        "Payment not found for order: " + orderId, "PAYMENT_NOT_FOUND"));

        if (!isAdmin && !payment.getUser().getId().equals(userId)) {
            throw new AuthException("Not authorized to view this payment", "FORBIDDEN");
        }
        return payment;
    }

    @Transactional(readOnly = true)
    public List<Payment> getPaymentsForUser(Long userId) {
        return paymentRepository.findByUserId(userId);
    }

    @Transactional(readOnly = true)
    public Payment getPaymentByTransactionId(String transactionId) {
        return paymentRepository.findByTransactionId(transactionId)
                .orElseThrow(() -> new AuthException(
                        "Payment not found: " + transactionId, "PAYMENT_NOT_FOUND"));
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    private Payment findPayment(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new AuthException(
                        "Payment not found: " + paymentId, "PAYMENT_NOT_FOUND"));
    }

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new AuthException(
                        "Order not found: " + orderId, "ORDER_NOT_FOUND"));
    }

    private static String generateTransactionId() {
        return "TXN-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
    }
}