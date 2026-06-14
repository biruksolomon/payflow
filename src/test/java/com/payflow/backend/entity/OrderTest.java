package com.payflow.backend.entity;

import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.entity.OrderItem;
import com.payflow.backend.domain.enums.FulfillmentStatus;
import com.payflow.backend.domain.enums.OrderStatus;
import com.payflow.backend.domain.enums.PaymentStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class OrderTest {

    @Test
    @DisplayName("Should identify pending order")
    void shouldIdentifyPendingOrder() {

        Order order = Order.builder()
                .orderStatus(OrderStatus.PENDING)
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
                .orderStatus(OrderStatus.PROCESSING)
                .build();

        assertTrue(order.isProcessing());
        assertFalse(order.isPending());
    }

    @Test
    @DisplayName("Should identify shipped order")
    void shouldIdentifyShippedOrder() {

        Order order = Order.builder()
                .orderStatus(OrderStatus.SHIPPED)
                .build();

        assertTrue(order.isShipped());
    }

    @Test
    @DisplayName("Should identify delivered order")
    void shouldIdentifyDeliveredOrder() {

        Order order = Order.builder()
                .orderStatus(OrderStatus.DELIVERED)
                .build();

        assertTrue(order.isDelivered());
    }

    @Test
    @DisplayName("Should identify cancelled order")
    void shouldIdentifyCancelledOrder() {

        Order order = Order.builder()
                .orderStatus(OrderStatus.CANCELLED)
                .build();

        assertTrue(order.isCancelled());
    }

    @Test
    @DisplayName("Should identify successful payment")
    void shouldIdentifySuccessfulPayment() {

        Order order = Order.builder()
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();

        assertTrue(order.isPaymentSuccessful());
    }

    @Test
    @DisplayName("Should identify unsuccessful payment")
    void shouldIdentifyUnsuccessfulPayment() {

        Order order = Order.builder()
                .paymentStatus(PaymentStatus.FAILED)
                .build();

        assertFalse(order.isPaymentSuccessful());
    }

    @Test
    @DisplayName("Should return zero items when list is empty")
    void shouldReturnZeroItems() {

        Order order = Order.builder().build();

        assertEquals(0, order.getItemCount());
    }

    @Test
    @DisplayName("Should add item to order")
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

        assertTrue(order.getItems().contains(item));
    }

    @Test
    @DisplayName("Should initialize items when null")
    void shouldInitializeItemsWhenNull() {

        Order order = Order.builder()
                .items(null)
                .build();

        OrderItem item = OrderItem.builder()
                .productName("Phone")
                .productSku("SKU-002")
                .quantity(1)
                .unitPrice(BigDecimal.valueOf(500))
                .build();

        order.addItem(item);

        assertNotNull(order.getItems());
        assertEquals(1, order.getItems().size());
    }

    @Test
    @DisplayName("Should apply builder defaults")
    void shouldApplyBuilderDefaults() {

        Order order = Order.builder().build();

        assertEquals(
                OrderStatus.PENDING,
                order.getOrderStatus()
        );

        assertEquals(
                PaymentStatus.PENDING,
                order.getPaymentStatus()
        );

        assertEquals(
                FulfillmentStatus.NOT_SHIPPED,
                order.getFulfillmentStatus()
        );

        assertEquals(
                BigDecimal.ZERO,
                order.getSubtotal()
        );

        assertEquals(
                BigDecimal.ZERO,
                order.getTaxAmount()
        );

        assertEquals(
                BigDecimal.ZERO,
                order.getShippingCost()
        );

        assertEquals(
                BigDecimal.ZERO,
                order.getDiscountAmount()
        );

        assertEquals(
                BigDecimal.ZERO,
                order.getTotalPrice()
        );

        assertEquals(
                "USD",
                order.getCurrency()
        );

        assertNotNull(order.getItems());

        assertTrue(order.getItems().isEmpty());
    }

    @Test
    @DisplayName("Should override builder defaults")
    void shouldOverrideDefaults() {

        Order order = Order.builder()
                .orderStatus(OrderStatus.PROCESSING)
                .paymentStatus(PaymentStatus.SUCCESS)
                .fulfillmentStatus(FulfillmentStatus.SHIPPED)
                .currency("EUR")
                .build();

        assertEquals(
                OrderStatus.PROCESSING,
                order.getOrderStatus()
        );

        assertEquals(
                PaymentStatus.SUCCESS,
                order.getPaymentStatus()
        );

        assertEquals(
                FulfillmentStatus.SHIPPED,
                order.getFulfillmentStatus()
        );

        assertEquals(
                "EUR",
                order.getCurrency()
        );
    }

    @Test
    @DisplayName("Should support equals and hashCode")
    void shouldSupportEqualsAndHashCode() {

        Order order1 = Order.builder()
                .id(1L)
                .orderNumber("ORD-001")
                .build();

        Order order2 = Order.builder()
                .id(1L)
                .orderNumber("ORD-001")
                .build();

        assertEquals(order1, order2);
        assertEquals(order1.hashCode(), order2.hashCode());
    }

    @Test
    @DisplayName("Should support toString")
    void shouldGenerateToString() {

        Order order = Order.builder()
                .orderNumber("ORD-001")
                .build();

        assertNotNull(order.toString());
    }
}