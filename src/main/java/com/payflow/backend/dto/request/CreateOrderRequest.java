package com.payflow.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for POST /api/orders.
 * Replaces the raw {@code Map<String, Object>} used previously.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateOrderRequest {

    @NotBlank(message = "Shipping street is required")
    @Size(max = 255, message = "Shipping street must be at most 255 characters")
    private String shippingStreet;

    @NotBlank(message = "Shipping city is required")
    @Size(max = 100, message = "Shipping city must be at most 100 characters")
    private String shippingCity;

    @Size(max = 100, message = "Shipping state must be at most 100 characters")
    private String shippingState;

    @NotBlank(message = "Shipping postal code is required")
    @Size(max = 20, message = "Shipping postal code must be at most 20 characters")
    private String shippingPostal;

    @NotBlank(message = "Shipping country is required")
    @Size(max = 100, message = "Shipping country must be at most 100 characters")
    private String shippingCountry;

    @Size(max = 1000, message = "Customer notes must be at most 1000 characters")
    private String customerNotes;
}
