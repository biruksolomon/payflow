package com.payflow.backend.entity;


import com.payflow.backend.domain.entity.CartItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CartItemTest {

    @Test
    @DisplayName("Should calculate subtotal correctly")
    void shouldCalculateSubtotal() {

        CartItem item = CartItem.builder()
                .quantity(5)
                .unitPrice(BigDecimal.valueOf(100))
                .build();

        assertEquals(
                BigDecimal.valueOf(500),
                item.getSubtotal()
        );
    }

    @Test
    @DisplayName("Should return zero subtotal when quantity is null")
    void shouldReturnZeroWhenQuantityNull() {

        CartItem item = CartItem.builder()
                .quantity(null)
                .unitPrice(BigDecimal.valueOf(100))
                .build();

        assertEquals(
                BigDecimal.ZERO,
                item.getSubtotal()
        );
    }

    @Test
    @DisplayName("Should return zero subtotal when price is null")
    void shouldReturnZeroWhenPriceNull() {

        CartItem item = CartItem.builder()
                .quantity(5)
                .unitPrice(null)
                .build();

        assertEquals(
                BigDecimal.ZERO,
                item.getSubtotal()
        );
    }

    @Test
    @DisplayName("Should return zero subtotal when both are null")
    void shouldReturnZeroWhenAllNull() {

        CartItem item = CartItem.builder()
                .quantity(null)
                .unitPrice(null)
                .build();

        assertEquals(
                BigDecimal.ZERO,
                item.getSubtotal()
        );
    }
}