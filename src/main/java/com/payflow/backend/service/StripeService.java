package com.payflow.backend.service;

import com.payflow.backend.config.StripeConfig;
import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.dto.request.CreatePaymentIntentRequest;
import com.payflow.backend.dto.response.CreatePaymentIntentResponse;
import com.payflow.backend.exception.AuthException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.PaymentIntent;
import com.stripe.model.Refund;
import com.stripe.net.Webhook;
import com.stripe.param.PaymentIntentCreateParams;
import com.stripe.param.RefundCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * StripeService — thin adapter between the PayFlow domain and the Stripe Java SDK.
 *
 * Responsibilities:
 *  - createPaymentIntent : creates a Stripe PaymentIntent, persists a PENDING Payment row
 *  - handleWebhookEvent  : verifies the webhook signature, routes events to PaymentService
 *  - createRefund        : issues a partial/full refund via the Stripe API
 *
 * All Stripe SDK calls that can throw StripeException are caught and re-thrown as
 * AuthException (uses the existing error-handling pattern already in the project).
 * The GlobalExceptionHandler has a dedicated handler for StripeException as well.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeConfig stripeConfig;
    private final PaymentService paymentService;

    // ─────────────────────────────────────────────────────────────
    // CREATE PAYMENT INTENT
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a Stripe PaymentIntent for the given order, then immediately
     * persists a PENDING Payment row via PaymentService.
     *
     * @param request contains orderId
     * @param userId  authenticated user making the payment
     * @return DTO carrying the client_secret (needed by the frontend) and internal paymentId
     */
    public CreatePaymentIntentResponse createPaymentIntent(
            CreatePaymentIntentRequest request, Long userId) {

        // 1. Fetch order total from PaymentService (dry-run initiate gives us the amount)
        //    We need to know the amount before we call Stripe.
        //    Strategy: initiate the payment first (creates a PENDING row), then create the
        //    Stripe PaymentIntent, then store the intent ID back on the payment.
        Payment pendingPayment = paymentService.initiatePayment(
                request.getOrderId(),
                userId,
                PaymentMethod.STRIPE,
                null,   // stripeIntentId — filled in below after SDK call
                null,
                null);

        // 2. Convert amount to cents (Stripe uses smallest currency unit)
        long amountInCents = pendingPayment.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        // 3. Call Stripe SDK
        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(amountInCents)
                .setCurrency(pendingPayment.getCurrency().name().toLowerCase())
                .setAutomaticPaymentMethods(
                        PaymentIntentCreateParams.AutomaticPaymentMethods.builder()
                                .setEnabled(true)
                                .build())
                .putMetadata("orderId",   String.valueOf(request.getOrderId()))
                .putMetadata("userId",    String.valueOf(userId))
                .putMetadata("paymentId", String.valueOf(pendingPayment.getId()))
                .build();

        PaymentIntent intent;
        try {
            intent = PaymentIntent.create(params);
        } catch (StripeException e) {
            log.error("Stripe PaymentIntent creation failed — orderId={} error={}",
                    request.getOrderId(), e.getMessage());
            // Mark the pending payment as failed so the order is not left in limbo
            paymentService.recordFailedPayment(
                    pendingPayment.getId(), e.getCode(), e.getMessage());
            throw new AuthException(
                    "Stripe payment intent creation failed: " + e.getMessage(),
                    "STRIPE_INTENT_FAILED");
        }

        // 4. Store the Stripe intent ID on the existing Payment row
        paymentService.updateStripeIntentId(pendingPayment.getId(), intent.getId());

        log.info("Stripe PaymentIntent created — intentId={} paymentId={} orderId={}",
                intent.getId(), pendingPayment.getId(), request.getOrderId());

        return CreatePaymentIntentResponse.builder()
                .clientSecret(intent.getClientSecret())
                .paymentIntentId(intent.getId())
                .paymentId(pendingPayment.getId())
                .amount(pendingPayment.getAmount())
                .currency(pendingPayment.getCurrency().name())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // WEBHOOK HANDLER
    // ─────────────────────────────────────────────────────────────

    /**
     * Verifies the Stripe-Signature header, parses the event, and routes it to
     * the appropriate PaymentService method.
     *
     * Handled events:
     *  - payment_intent.succeeded  → recordSuccessfulPayment
     *  - payment_intent.payment_failed → recordFailedPayment
     *  - charge.refunded           → completeRefund
     *
     * @param payload       raw request body as a String (must NOT be parsed before)
     * @param sigHeader     value of the "Stripe-Signature" HTTP header
     */
    public void handleWebhookEvent(String payload, String sigHeader) {
        Event event;

        // Verify signature to ensure the request genuinely comes from Stripe
        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed — {}", e.getMessage());
            throw new AuthException(
                    "Invalid Stripe webhook signature", "WEBHOOK_SIGNATURE_INVALID");
        }

        log.info("Stripe webhook received — type={} id={}", event.getType(), event.getId());

        switch (event.getType()) {

            case "payment_intent.succeeded" -> {
                PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new AuthException(
                                "Cannot deserialize payment_intent.succeeded", "WEBHOOK_PARSE_ERROR"));

                String paymentIdMeta = intent.getMetadata().get("paymentId");
                if (paymentIdMeta == null) {
                    log.warn("payment_intent.succeeded received without paymentId metadata — intentId={}",
                            intent.getId());
                    return;
                }

                Long paymentId = Long.parseLong(paymentIdMeta);
                paymentService.recordSuccessfulPayment(paymentId);
                log.info("payment_intent.succeeded processed — paymentId={}", paymentId);
            }

            case "payment_intent.payment_failed" -> {
                PaymentIntent intent = (PaymentIntent) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new AuthException(
                                "Cannot deserialize payment_intent.payment_failed", "WEBHOOK_PARSE_ERROR"));

                String paymentIdMeta = intent.getMetadata().get("paymentId");
                if (paymentIdMeta == null) {
                    log.warn("payment_intent.payment_failed received without paymentId metadata — intentId={}",
                            intent.getId());
                    return;
                }

                Long paymentId = Long.parseLong(paymentIdMeta);
                String errorCode    = intent.getLastPaymentError() != null
                        ? intent.getLastPaymentError().getCode() : "PAYMENT_FAILED";
                String errorMessage = intent.getLastPaymentError() != null
                        ? intent.getLastPaymentError().getMessage() : "Payment failed via Stripe";

                paymentService.recordFailedPayment(paymentId, errorCode, errorMessage);
                log.info("payment_intent.payment_failed processed — paymentId={}", paymentId);
            }

            case "charge.refunded" -> {
                // charge.refunded fires after a refund is fully confirmed on Stripe's side.
                // We look up the payment by stripePaymentIntentId and complete the refund.
                com.stripe.model.Charge charge =
                        (com.stripe.model.Charge) event.getDataObjectDeserializer()
                                .getObject()
                                .orElseThrow(() -> new AuthException(
                                        "Cannot deserialize charge.refunded", "WEBHOOK_PARSE_ERROR"));

                paymentService.completeRefundByIntentId(charge.getPaymentIntent());
                log.info("charge.refunded processed — intentId={}", charge.getPaymentIntent());
            }

            default -> log.debug("Unhandled Stripe webhook event — type={}", event.getType());
        }
    }

    // ─────────────────────────────────────────────────────────────
    // REFUND
    // ─────────────────────────────────────────────────────────────

    /**
     * Issues a refund via the Stripe API for a given PaymentIntent.
     * Call this BEFORE calling PaymentService.initiateRefund so the
     * gateway action happens first; if Stripe rejects it, no state change occurs.
     *
     * @param stripePaymentIntentId the intent to refund
     * @param amount                amount in dollars (converted to cents internally); null = full refund
     * @return the created Stripe Refund object
     */
    public Refund createRefund(String stripePaymentIntentId, BigDecimal amount) {
        RefundCreateParams.Builder builder = RefundCreateParams.builder()
                .setPaymentIntent(stripePaymentIntentId);

        if (amount != null && amount.compareTo(BigDecimal.ZERO) > 0) {
            builder.setAmount(amount.multiply(BigDecimal.valueOf(100)).longValue());
        }

        try {
            Refund refund = Refund.create(builder.build());
            log.info("Stripe refund created — refundId={} intentId={} amount={}",
                    refund.getId(), stripePaymentIntentId, amount);
            return refund;
        } catch (StripeException e) {
            log.error("Stripe refund failed — intentId={} error={}", stripePaymentIntentId, e.getMessage());
            throw new AuthException(
                    "Stripe refund failed: " + e.getMessage(), "STRIPE_REFUND_FAILED");
        }
    }
}