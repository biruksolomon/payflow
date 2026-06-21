package com.payflow.backend.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for PUT /api/cart/items/{productId}.
 * A quantity of 0 removes the item from the cart.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateCartItemRequest {

    @NotNull(message = "quantity is required")
    @Min(value = 0, message = "Quantity must be 0 or more (0 removes the item)")
    private Integer quantity;
}
