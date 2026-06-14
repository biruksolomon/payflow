package com.payflow.backend.repository;

import com.payflow.backend.domain.entity.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class OrderItemRepositoryTest {

    @Autowired
    private OrderItemRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void shouldFindItemsByOrderId() {

        User user = User.builder()
                .email("test@test.com")
                .passwordHash("hash")
                .firstName("John")
                .lastName("Doe")
                .build();

        entityManager.persist(user);

        Order order = Order.builder()
                .orderNumber("ORD-1")
                .user(user)
                .shippingAddressStreet("Street")
                .shippingAddressCity("City")
                .shippingAddressPostalCode("1000")
                .shippingAddressCountry("ETH")
                .build();

        entityManager.persist(order);

        Product product = Product.builder()
                .sku("SKU-1")
                .name("Laptop")
                .category("Electronics")
                .price(BigDecimal.valueOf(1000))
                .build();

        entityManager.persist(product);

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .productName("Laptop")
                .productSku("SKU-1")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(1000))
                .build();

        entityManager.persist(item);
        entityManager.flush();

        List<OrderItem> items =
                repository.findByOrderId(order.getId());

        assertEquals(1, items.size());
    }
}