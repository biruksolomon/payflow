package com.payflow.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response body for POST /api/payments/stripe/create-checkout-session.
 *
 * The frontend redirects the user to {@code checkoutUrl} (a Stripe-hosted page).
 * After payment, Stripe redirects to success_url or cancel_url with
 * {@code session_id} appended as a query param so the frontend can confirm.
 *
 * {@code paymentId} is the internal PayFlow Payment row ID for correlation.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateCheckoutSessionResponse {

    /** Stripe-hosted checkout page URL — redirect the user here. */
    @JsonProperty("checkout_url")
    private String checkoutUrl;

    /** Stripe Checkout Session ID (cs_live_... or cs_test_...). */
    @JsonProperty("session_id")
    private String sessionId;

    /** Internal PayFlow Payment row ID for status polling. */
    @JsonProperty("payment_id")
    private Long paymentId;

    private BigDecimal amount;

    private String currency;
}