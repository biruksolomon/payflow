package com.payflow.backend.repository;

import com.payflow.backend.domain.entity.Cart;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import jakarta.persistence.EntityManager;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class CartRepositoryTest {

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("Should find cart by user id")
    void shouldFindCartByUserId() {

        User user = User.builder()
                .email("cart@test.com")
                .passwordHash("hash")
                .firstName("Cart")
                .lastName("User")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        entityManager.persist(user);

        Cart cart = Cart.builder()
                .user(user)
                .build();

        entityManager.persist(cart);
        entityManager.flush();

        Optional<Cart> result =
                cartRepository.findByUserId(user.getId());

        assertTrue(result.isPresent());

        assertEquals(
                user.getId(),
                result.get().getUser().getId()
        );
    }

    @Test
    @DisplayName("Should return empty when cart not found")
    void shouldReturnEmptyWhenCartNotFound() {

        Optional<Cart> result =
                cartRepository.findByUserId(999L);

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find cart with items")
    void shouldFindCartWithItems() {

        User user = User.builder()
                .email("items@test.com")
                .passwordHash("hash")
                .firstName("Test")
                .lastName("User")
                .build();

        entityManager.persist(user);

        Cart cart = Cart.builder()
                .user(user)
                .build();

        entityManager.persist(cart);
        entityManager.flush();

        Optional<Cart> result =
                cartRepository.findByUserIdWithItems(user.getId());

        assertTrue(result.isPresent());
    }
}