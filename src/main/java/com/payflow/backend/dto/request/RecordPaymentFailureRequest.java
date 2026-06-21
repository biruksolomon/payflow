package com.payflow.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/payments/{id}/failure.
 * Both fields are optional; sensible defaults are applied by the service when absent.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RecordPaymentFailureRequest {

    @Size(max = 100, message = "Error code must be at most 100 characters")
    @Builder.Default
    private String errorCode = "PAYMENT_FAILED";

    @Size(max = 500, message = "Error message must be at most 500 characters")
    @Builder.Default
    private String errorMessage = "Payment failed";
}
