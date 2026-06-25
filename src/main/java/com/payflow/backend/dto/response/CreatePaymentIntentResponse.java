package com.payflow.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response body for POST /api/payments/stripe/create-intent.
 *
 * The frontend uses {@code clientSecret} to call stripe.confirmCardPayment().
 * {@code paymentId} is the internal PayFlow Payment row ID — send it back to
 * POST /api/payments/{id}/success or /{id}/failure if you prefer manual confirmation
 * instead of webhooks.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentIntentResponse {

    @JsonProperty("client_secret")
    private String clientSecret;

    @JsonProperty("payment_intent_id")
    private String paymentIntentId;

    @JsonProperty("payment_id")
    private Long paymentId;

    private BigDecimal amount;

    private String currency;
}