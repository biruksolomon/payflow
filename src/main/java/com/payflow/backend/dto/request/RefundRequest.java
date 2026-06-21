package com.payflow.backend.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Optional request body for POST /api/payments/orders/{orderId}/refund.
 * When refundAmount is null the full remaining payment amount is refunded.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefundRequest {

    @DecimalMin(value = "0.01", message = "Refund amount must be greater than 0")
    @Digits(integer = 17, fraction = 2, message = "Refund amount must have at most 2 decimal places")
    private BigDecimal refundAmount;
}
