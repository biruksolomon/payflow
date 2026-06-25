package com.payflow.backend.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/payments/stripe/create-intent.
 * The frontend sends the orderId; the server resolves the amount from the Order.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePaymentIntentRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;
}