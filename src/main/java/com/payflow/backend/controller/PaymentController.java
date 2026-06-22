package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.dto.request.InitiatePaymentRequest;
import com.payflow.backend.dto.request.RecordPaymentFailureRequest;
import com.payflow.backend.dto.request.RefundRequest;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/payments")
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Payment initiation and reconciliation")
@SecurityRequirement(name = "Bearer Authentication")
public class PaymentController {

    private final PaymentService paymentService;

    // ─────────────────────────────────────────────────────────────
    // INITIATE PAYMENT
    // ─────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Initiate a payment for an order")
    public ResponseEntity<Payment> initiatePayment(
            @Valid @RequestBody InitiatePaymentRequest request,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);

        Payment payment = paymentService.initiatePayment(
                request.getOrderId(),
                userId,
                request.getPaymentMethod(),
                request.getStripeIntentId(),
                request.getCardLastFour(),
                request.getCardBrand());

        log.info("Payment initiated — paymentId={} orderId={} userId={}", payment.getId(), request.getOrderId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(payment);
    }

    // ─────────────────────────────────────────────────────────────
    // RECORD SUCCESS / FAILURE (gateway webhooks or internal calls)
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/success")
    @Operation(summary = "Record a successful payment (admin / webhook)")
    public ResponseEntity<Payment> recordSuccess(@PathVariable Long id) {
        Payment payment = paymentService.recordSuccessfulPayment(id);
        log.info("Payment success recorded — paymentId={}", id);
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/{id}/failure")
    @Operation(summary = "Record a failed payment (admin / webhook)")
    public ResponseEntity<Payment> recordFailure(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) RecordPaymentFailureRequest request) {

        String errorCode    = request != null && request.getErrorCode() != null    ? request.getErrorCode()    : "PAYMENT_FAILED";
        String errorMessage = request != null && request.getErrorMessage() != null ? request.getErrorMessage() : "Payment failed";

        Payment payment = paymentService.recordFailedPayment(id, errorCode, errorMessage);
        log.info("Payment failure recorded — paymentId={}", id);
        return ResponseEntity.ok(payment);
    }

    // ─────────────────────────────────────────────────────────────
    // REFUNDS
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/orders/{orderId}/refund")
    @Operation(summary = "Initiate a refund for an order's payment")
    public ResponseEntity<Payment> initiateRefund(
            @PathVariable Long orderId,
            @Valid @RequestBody(required = false) RefundRequest request,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);

        Payment payment = paymentService.initiateRefund(
                orderId,
                userDetails.getId(),
                userDetails.hasAdminPrivileges(),
                request != null ? request.getRefundAmount() : null);

        log.info("Refund initiated — orderId={} userId={}", orderId, userDetails.getId());
        return ResponseEntity.ok(payment);
    }

    @PostMapping("/{id}/complete-refund")
    @Operation(summary = "Complete a refund after gateway confirmation (admin / webhook)")
    public ResponseEntity<Payment> completeRefund(@PathVariable Long id) {
        Payment payment = paymentService.completeRefund(id);
        log.info("Refund completed — paymentId={}", id);
        return ResponseEntity.ok(payment);
    }

    // ─────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/my")
    @Operation(summary = "Get all payments for the authenticated user")
    public ResponseEntity<List<Payment>> getMyPayments(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return ResponseEntity.ok(paymentService.getPaymentsForUser(userId));
    }

    @GetMapping("/orders/{orderId}")
    @Operation(summary = "Get payment for a specific order")
    public ResponseEntity<Payment> getPaymentByOrder(
            @PathVariable Long orderId,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);
        Payment payment = paymentService.getPaymentByOrderId(orderId, userDetails.getId(), userDetails.hasAdminPrivileges());
        return ResponseEntity.ok(payment);
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "Get payment by transaction ID (admin only)")
    public ResponseEntity<Payment> getByTransactionId(@PathVariable String transactionId) {
        return ResponseEntity.ok(paymentService.getPaymentByTransactionId(transactionId));
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private PayFlowUserDetails resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required", "UNAUTHORIZED");
        }
        return (PayFlowUserDetails) authentication.getPrincipal();
    }

    private Long resolveUserId(Authentication authentication) {
        return resolveUser(authentication).getId();
    }
}
