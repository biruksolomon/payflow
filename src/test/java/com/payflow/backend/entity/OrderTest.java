package com.payflow.backend.entity;


import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.entity.OrderItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {


    @Test
    @DisplayName("Should identify pending order")
    void shouldIdentifyPendingOrder() {

        Order order = Order.builder()
                .orderStatus("PENDING")
                .build();

        assertTrue(order.isPending());
        assertFalse(order.isProcessing());
        assertFalse(order.isShipped());
        assertFalse(order.isDelivered());
        assertFalse(order.isCancelled());
    }

    @Test
    @DisplayName("Should identify processing order")
    void shouldIdentifyProcessingOrder() {

        Order order = Order.builder()
                .orderStatus("PROCESSING")
                .build();

        assertTrue(order.isProcessing());
        assertFalse(order.isPending());
    }

    @Test
    @DisplayName("Should identify shipped order")
    void shouldIdentifyShippedOrder() {

        Order order = Order.builder()
                .orderStatus("SHIPPED")
                .build();

        assertTrue(order.isShipped());
        assertFalse(order.isDelivered());
    }

    @Test
    @DisplayName("Should identify delivered order")
    void shouldIdentifyDeliveredOrder() {

        Order order = Order.builder()
                .orderStatus("DELIVERED")
                .build();

        assertTrue(order.isDelivered());
    }

    @Test
    @DisplayName("Should identify cancelled order")
    void shouldIdentifyCancelledOrder() {

        Order order = Order.builder()
                .orderStatus("CANCELLED")
                .build();

        assertTrue(order.isCancelled());
    }

    @Test
    @DisplayName("Should identify successful payment")
    void shouldIdentifySuccessfulPayment() {

        Order order = Order.builder()
                .paymentStatus("SUCCESS")
                .build();

        assertTrue(order.isPaymentSuccessful());
    }

    @Test
    @DisplayName("Should identify unsuccessful payment")
    void shouldIdentifyUnsuccessfulPayment() {

        Order order = Order.builder()
                .paymentStatus("FAILED")
                .build();

        assertFalse(order.isPaymentSuccessful());
    }

    @Test
    @DisplayName("Should return zero item count when list is empty")
    void shouldReturnZeroItemCount() {

        Order order = Order.builder().build();

        assertEquals(0, order.getItemCount());
    }

    @Test
    @DisplayName("Should add item and maintain relationship")
    void shouldAddItem() {

        Order order = Order.builder().build();

        OrderItem item = OrderItem.builder()
                .productName("Laptop")
                .productSku("SKU-001")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(1000))
                .build();

        order.addItem(item);

        assertEquals(1, order.getItemCount());
        assertEquals(order, item.getOrder());
    }

    @Test
    @DisplayName("Should apply builder defaults")
    void shouldApplyDefaults() {

        Order order = Order.builder().build();

        assertEquals("PENDING", order.getOrderStatus());
        assertEquals("PENDING", order.getPaymentStatus());
        assertEquals("NOT_SHIPPED", order.getFulfillmentStatus());

        assertEquals(BigDecimal.ZERO, order.getSubtotal());
        assertEquals(BigDecimal.ZERO, order.getTaxAmount());
        assertEquals(BigDecimal.ZERO, order.getShippingCost());
        assertEquals(BigDecimal.ZERO, order.getDiscountAmount());
        assertEquals(BigDecimal.ZERO, order.getTotalPrice());

        assertEquals("USD", order.getCurrency());

        assertNotNull(order.getItems());
        assertTrue(order.getItems().isEmpty());
    }

    @Test
    @DisplayName("Should override builder defaults")
    void shouldOverrideDefaults() {

        Order order = Order.builder()
                .orderStatus("PROCESSING")
                .paymentStatus("SUCCESS")
                .fulfillmentStatus("SHIPPED")
                .currency("EUR")
                .build();

        assertEquals("PROCESSING", order.getOrderStatus());
        assertEquals("SUCCESS", order.getPaymentStatus());
        assertEquals("SHIPPED", order.getFulfillmentStatus());
        assertEquals("EUR", order.getCurrency());
    }

}
