package com.payflow.backend.repository;

import com.payflow.backend.domain.entity.*;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import jakarta.persistence.EntityManager;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class CartItemRepositoryTest {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Should find cart item by cart and product")
    void shouldFindCartItemByCartAndProduct() {

        User user = User.builder()
                .email("cartitem@test.com")
                .passwordHash("hash")
                .firstName("User")
                .lastName("One")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        entityManager.persist(user);

        Cart cart = Cart.builder()
                .user(user)
                .build();

        entityManager.persist(cart);

        Product product = Product.builder()
                .sku("SKU-001")
                .name("Laptop")
                .category("Electronics")
                .price(BigDecimal.valueOf(1000))
                .currency(Currency.USD)
                .build();

        entityManager.persist(product);

        CartItem cartItem = CartItem.builder()
                .cart(cart)
                .product(product)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(1000))
                .build();

        entityManager.persist(cartItem);

        entityManager.flush();

        Optional<CartItem> result =
                cartItemRepository.findByCartIdAndProductId(
                        cart.getId(),
                        product.getId()
                );

        assertTrue(result.isPresent());

        assertEquals(
                cartItem.getId(),
                result.get().getId()
        );
    }

    @Test
    @DisplayName("Should find all items by cart id")
    void shouldFindItemsByCartId() {

        User user = User.builder()
                .email("multi@test.com")
                .passwordHash("hash")
                .firstName("User")
                .lastName("One")
                .build();

        entityManager.persist(user);

        Cart cart = Cart.builder()
                .user(user)
                .build();

        entityManager.persist(cart);

        Product product1 = Product.builder()
                .sku("SKU-100")
                .name("Phone")
                .category("Electronics")
                .price(BigDecimal.valueOf(500))
                .build();

        Product product2 = Product.builder()
                .sku("SKU-101")
                .name("Laptop")
                .category("Electronics")
                .price(BigDecimal.valueOf(1000))
                .build();

        entityManager.persist(product1);
        entityManager.persist(product2);

        entityManager.persist(
                CartItem.builder()
                        .cart(cart)
                        .product(product1)
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(500))
                        .build()
        );

        entityManager.persist(
                CartItem.builder()
                        .cart(cart)
                        .product(product2)
                        .quantity(1)
                        .unitPrice(BigDecimal.valueOf(1000))
                        .build()
        );

        entityManager.flush();

        List<CartItem> items =
                cartItemRepository.findByCartId(cart.getId());

        assertEquals(2, items.size());
    }

    @Test
    @DisplayName("Should return empty when item does not exist")
    void shouldReturnEmptyWhenNotFound() {

        Optional<CartItem> result =
                cartItemRepository.findByCartIdAndProductId(
                        1L,
                        999L
                );

        assertTrue(result.isEmpty());
    }
}