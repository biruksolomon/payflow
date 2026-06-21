package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);

        Long orderId = parseLongRequired(body.get("orderId"), "orderId");

        PaymentMethod method;
        try {
            method = PaymentMethod.valueOf(String.valueOf(body.get("paymentMethod")).toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new AuthException("Invalid payment method: " + body.get("paymentMethod"), "INVALID_PAYMENT_METHOD", HttpStatus.BAD_REQUEST);
        }

        Payment payment = paymentService.initiatePayment(
                orderId,
                userId,
                method,
                (String) body.get("stripeIntentId"),
                (String) body.get("cardLastFour"),
                (String) body.get("cardBrand"));

        log.info("Payment initiated — paymentId={} orderId={} userId={}", payment.getId(), orderId, userId);
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
            @RequestBody Map<String, String> body) {

        String errorCode    = body.getOrDefault("errorCode", "PAYMENT_FAILED");
        String errorMessage = body.getOrDefault("errorMessage", "Payment failed");

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
            @RequestBody(required = false) Map<String, Object> body,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);
        boolean isAdmin = userDetails.isAdmin();

        BigDecimal refundAmount = body != null ? parseBigDecimal(body.get("refundAmount")) : null;

        Payment payment = paymentService.initiateRefund(orderId, userDetails.getId(), isAdmin, refundAmount);
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
        boolean isAdmin = userDetails.isAdmin();
        Payment payment = paymentService.getPaymentByOrderId(orderId, userDetails.getId(), isAdmin);
        return ResponseEntity.ok(payment);
    }

    @GetMapping("/transaction/{transactionId}")
    @Operation(summary = "Get payment by transaction ID (admin only)")
    public ResponseEntity<Payment> getByTransactionId(@PathVariable String transactionId) {
        return ResponseEntity.ok(paymentService.getPaymentByTransactionId(transactionId));
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────���───────────────────────────────────────────────

    private PayFlowUserDetails resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required", "UNAUTHORIZED");
        }
        return (PayFlowUserDetails) authentication.getPrincipal();
    }

    private Long resolveUserId(Authentication authentication) {
        return resolveUser(authentication).getId();
    }

    private Long parseLongRequired(Object value, String field) {
        if (value == null) throw new AuthException(field + " is required", "INVALID_REQUEST", HttpStatus.BAD_REQUEST);
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); }
        catch (Exception e) { throw new AuthException(field + " must be a number", "INVALID_REQUEST", HttpStatus.BAD_REQUEST); }
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(value.toString()); } catch (Exception e) { return null; }
    }
}
