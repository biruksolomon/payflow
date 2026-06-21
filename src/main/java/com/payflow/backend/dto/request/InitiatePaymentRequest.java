package com.payflow.backend.dto.request;

import com.payflow.backend.domain.enums.PaymentMethod;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/payments.
 * Replaces the raw {@code Map<String, Object>} used previously.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InitiatePaymentRequest {

    @NotNull(message = "orderId is required")
    private Long orderId;

    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;

    @Size(max = 255, message = "Stripe intent ID must be at most 255 characters")
    private String stripeIntentId;

    @Size(max = 4, min = 4, message = "Card last four must be exactly 4 characters")
    private String cardLastFour;

    @Size(max = 50, message = "Card brand must be at most 50 characters")
    private String cardBrand;
}
