package com.payflow.backend.entity;


import com.payflow.backend.domain.entity.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderItemTest {

    @Test
    @DisplayName("Should calculate subtotal correctly")
    void shouldCalculateSubtotal() {

        OrderItem item = OrderItem.builder()
                .quantity(3)
                .unitPrice(BigDecimal.valueOf(100))
                .build();

        assertEquals(
                BigDecimal.valueOf(300),
                item.getSubtotal()
        );
    }

    @Test
    @DisplayName("Should return zero subtotal when quantity is null")
    void shouldReturnZeroWhenQuantityIsNull() {

        OrderItem item = OrderItem.builder()
                .quantity(null)
                .unitPrice(BigDecimal.valueOf(100))
                .build();

        assertEquals(
                BigDecimal.ZERO,
                item.getSubtotal()
        );
    }

    @Test
    @DisplayName("Should return zero subtotal when unit price is null")
    void shouldReturnZeroWhenPriceIsNull() {

        OrderItem item = OrderItem.builder()
                .quantity(5)
                .unitPrice(null)
                .build();

        assertEquals(
                BigDecimal.ZERO,
                item.getSubtotal()
        );
    }

    @Test
    @DisplayName("Should return zero subtotal when both values are null")
    void shouldReturnZeroWhenAllValuesNull() {

        OrderItem item = OrderItem.builder()
                .quantity(null)
                .unitPrice(null)
                .build();

        assertEquals(
                BigDecimal.ZERO,
                item.getSubtotal()
        );
    }

    @Test
    @DisplayName("Should apply builder defaults")
    void shouldApplyBuilderDefaults() {

        OrderItem item = OrderItem.builder()
                .productName("Laptop")
                .productSku("SKU-001")
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(1000))
                .build();

        assertEquals(
                BigDecimal.ZERO,
                item.getDiscountPerUnit()
        );
    }

    @Test
    @DisplayName("Should override default discount")
    void shouldOverrideDiscount() {

        OrderItem item = OrderItem.builder()
                .productName("Laptop")
                .productSku("SKU-001")
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(1000))
                .discountPerUnit(BigDecimal.valueOf(50))
                .build();

        assertEquals(
                BigDecimal.valueOf(50),
                item.getDiscountPerUnit()
        );
    }

    @Test
    @DisplayName("Should create valid order item")
    void shouldCreateOrderItem() {

        OrderItem item = OrderItem.builder()
                .productName("Laptop")
                .productSku("SKU-001")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(1000))
                .build();

        assertNotNull(item);

        assertEquals("Laptop", item.getProductName());
        assertEquals("SKU-001", item.getProductSku());
        assertEquals(2, item.getQuantity());
    }


}
