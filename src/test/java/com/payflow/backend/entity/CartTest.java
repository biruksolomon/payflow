package com.payflow.backend.entity;


import com.payflow.backend.domain.entity.Cart;
import com.payflow.backend.domain.entity.CartItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class CartTest {

    @Test
    @DisplayName("Should add item to cart")
    void shouldAddItemToCart() {

        Cart cart = Cart.builder().build();

        CartItem item = CartItem.builder()
                .quantity(2)
                .unitPrice(BigDecimal.TEN)
                .build();

        cart.addItem(item);

        assertEquals(
                1,
                cart.getItems().size()
        );

        assertEquals(
                cart,
                item.getCart()
        );
    }

    @Test
    @DisplayName("Should remove item from cart")
    void shouldRemoveItemFromCart() {

        Cart cart = Cart.builder().build();

        CartItem item = CartItem.builder()
                .quantity(2)
                .unitPrice(BigDecimal.TEN)
                .build();

        cart.addItem(item);

        cart.removeItem(item);

        assertTrue(
                cart.getItems().isEmpty()
        );
    }

    @Test
    @DisplayName("Should return true when cart is empty")
    void shouldReturnTrueWhenEmpty() {

        Cart cart = Cart.builder().build();

        assertTrue(
                cart.isEmpty()
        );
    }

    @Test
    @DisplayName("Should return false when cart contains items")
    void shouldReturnFalseWhenNotEmpty() {

        Cart cart = Cart.builder().build();

        CartItem item = CartItem.builder()
                .quantity(1)
                .unitPrice(BigDecimal.ONE)
                .build();

        cart.addItem(item);

        assertFalse(
                cart.isEmpty()
        );
    }

    @Test
    @DisplayName("Should initialize builder defaults")
    void shouldApplyBuilderDefaults() {

        Cart cart = Cart.builder().build();

        assertEquals(
                Integer.valueOf(0),
                cart.getTotalItems()
        );

        assertEquals(
                BigDecimal.ZERO,
                cart.getSubtotal()
        );

        assertEquals(
                BigDecimal.ZERO,
                cart.getTaxAmount()
        );

        assertEquals(
                BigDecimal.ZERO,
                cart.getDiscountAmount()
        );

        assertEquals(
                BigDecimal.ZERO,
                cart.getTotalPrice()
        );

        assertNotNull(
                cart.getItems()
        );

        assertTrue(
                cart.getItems().isEmpty()
        );
    }
}
