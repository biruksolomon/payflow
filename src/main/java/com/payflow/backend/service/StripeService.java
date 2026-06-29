package com.payflow.backend.service;

import com.payflow.backend.config.StripeConfig;
import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.dto.request.CreateCheckoutSessionRequest;
import com.payflow.backend.dto.response.CreateCheckoutSessionResponse;
import com.payflow.backend.exception.AuthException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.Refund;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.RefundCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

/**
 * StripeService — thin adapter between the PayFlow domain and the Stripe Java SDK.
 *
 * Uses Stripe Checkout Sessions so Stripe hosts the payment page and returns a URL.
 * The frontend simply redirects the user to {@code checkoutUrl}; no card SDK needed.
 *
 * Responsibilities:
 *  - createCheckoutSession : creates a Stripe Checkout Session, persists a PENDING Payment row,
 *                            returns the hosted checkout URL to the caller.
 *  - handleWebhookEvent    : verifies the webhook signature, routes events to PaymentService.
 *  - createRefund          : issues a partial / full refund via the Stripe API.
 *
 * Webhook events handled:
 *  - checkout.session.completed      → recordSuccessfulPayment
 *  - checkout.session.expired        → recordFailedPayment
 *  - charge.refunded                 → completeRefundByIntentId
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StripeService {

    private final StripeConfig stripeConfig;
    private final PaymentService paymentService;

    // ─────────────────────────────────────────────────────────────
    // CREATE CHECKOUT SESSION
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a Stripe Checkout Session for the given order.
     *
     * Flow:
     *  1. Initiate a PENDING Payment row via PaymentService (gives us amount + paymentId).
     *  2. Build a Stripe Checkout Session with one line-item for the order total.
     *  3. Store the Stripe session ID on the Payment row.
     *  4. Return the hosted checkout URL to the controller.
     *
     * @param request  contains orderId
     * @param userId   authenticated user making the payment
     * @return DTO carrying {@code checkoutUrl} (redirect target) and internal {@code paymentId}
     */
    public CreateCheckoutSessionResponse createCheckoutSession(
            CreateCheckoutSessionRequest request, Long userId) {

        // 1. Persist a PENDING Payment row so we have an ID before calling Stripe
        Payment pendingPayment = paymentService.initiatePayment(
                request.getOrderId(),
                userId,
                PaymentMethod.STRIPE,
                null,   // stripeIntentId — filled in below after SDK call
                null,
                null);

        // 2. Convert amount to cents (Stripe uses the smallest currency unit)
        long amountInCents = pendingPayment.getAmount()
                .multiply(BigDecimal.valueOf(100))
                .longValue();

        // Append paymentId to success/cancel URLs so the frontend can correlate
        String successUrl = stripeConfig.getSuccessUrl()
                + "?session_id={CHECKOUT_SESSION_ID}&payment_id=" + pendingPayment.getId();
        String cancelUrl  = stripeConfig.getCancelUrl()
                + "?payment_id=" + pendingPayment.getId();

        // 3. Build the Checkout Session params
        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency(pendingPayment.getCurrency().name().toLowerCase())
                                                .setUnitAmount(amountInCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("Order #" + request.getOrderId())
                                                                .setDescription("PayFlow order payment")
                                                                .build())
                                                .build())
                                .build())
                .putMetadata("orderId",   String.valueOf(request.getOrderId()))
                .putMetadata("userId",    String.valueOf(userId))
                .putMetadata("paymentId", String.valueOf(pendingPayment.getId()))
                .build();

        Session session;
        try {
            session = Session.create(params);
        } catch (StripeException e) {
            log.error("Stripe Checkout Session creation failed — orderId={} error={}",
                    request.getOrderId(), e.getMessage());
            // Mark the pending payment as failed so the order is not left in limbo
            paymentService.recordFailedPayment(
                    pendingPayment.getId(), e.getCode(), e.getMessage());
            throw new AuthException(
                    "Stripe checkout session creation failed: " + e.getMessage(),
                    "STRIPE_SESSION_FAILED");
        }

        // 4. Store the Stripe session ID on the Payment row for later webhook correlation
        paymentService.updateStripeIntentId(pendingPayment.getId(), session.getId());

        log.info("Stripe Checkout Session created — sessionId={} paymentId={} orderId={} url={}",
                session.getId(), pendingPayment.getId(), request.getOrderId(), session.getUrl());

        return CreateCheckoutSessionResponse.builder()
                .checkoutUrl(session.getUrl())
                .sessionId(session.getId())
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
     *  - checkout.session.completed  → recordSuccessfulPayment (via metadata.paymentId)
     *  - checkout.session.expired    → recordFailedPayment
     *  - charge.refunded             → completeRefundByIntentId
     *
     * @param payload    raw request body as a String (must NOT be parsed before this call)
     * @param sigHeader  value of the "Stripe-Signature" HTTP header
     */
    public void handleWebhookEvent(String payload, String sigHeader) {
        Event event;

        try {
            event = Webhook.constructEvent(payload, sigHeader, stripeConfig.getWebhookSecret());
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed — {}", e.getMessage());
            throw new AuthException(
                    "Invalid Stripe webhook signature", "WEBHOOK_SIGNATURE_INVALID");
        }

        log.info("Stripe webhook received — type={} id={}", event.getType(), event.getId());

        switch (event.getType()) {

            case "checkout.session.completed" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new AuthException(
                                "Cannot deserialize checkout.session.completed", "WEBHOOK_PARSE_ERROR"));

                String paymentIdMeta = session.getMetadata().get("paymentId");
                if (paymentIdMeta == null) {
                    log.warn("checkout.session.completed received without paymentId metadata — sessionId={}",
                            session.getId());
                    return;
                }

                Long paymentId = Long.parseLong(paymentIdMeta);
                paymentService.recordSuccessfulPayment(paymentId);
                log.info("checkout.session.completed processed — paymentId={}", paymentId);
            }

            case "checkout.session.expired" -> {
                Session session = (Session) event.getDataObjectDeserializer()
                        .getObject()
                        .orElseThrow(() -> new AuthException(
                                "Cannot deserialize checkout.session.expired", "WEBHOOK_PARSE_ERROR"));

                String paymentIdMeta = session.getMetadata().get("paymentId");
                if (paymentIdMeta == null) {
                    log.warn("checkout.session.expired received without paymentId metadata — sessionId={}",
                            session.getId());
                    return;
                }

                Long paymentId = Long.parseLong(paymentIdMeta);
                paymentService.recordFailedPayment(paymentId, "SESSION_EXPIRED",
                        "Stripe Checkout Session expired before payment was completed");
                log.info("checkout.session.expired processed — paymentId={}", paymentId);
            }

            case "charge.refunded" -> {
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
     * @param stripePaymentIntentId  the PaymentIntent to refund (pi_... attached to the session)
     * @param amount                 amount in dollars (converted to cents internally); null = full refund
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
            log.error("Stripe refund failed — intentId={} error={}; code: {}",
                    stripePaymentIntentId, e.getMessage(), e.getCode());
            throw new AuthException(
                    "Stripe refund failed: " + e.getMessage(), "STRIPE_REFUND_FAILED");
        }
    }
}
