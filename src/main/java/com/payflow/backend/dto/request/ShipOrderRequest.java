package com.payflow.backend.dto.request;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional request body for POST /api/orders/{id}/ship.
 * trackingNumber is optional; the field is null when not provided.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShipOrderRequest {

    @Size(max = 255, message = "Tracking number must be at most 255 characters")
    private String trackingNumber;
}
