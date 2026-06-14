package com.payflow.backend.repository;

import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.entity.OrderItem;
import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.OrderStatus;
import com.payflow.backend.domain.enums.PaymentStatus;
import com.payflow.backend.domain.enums.UserRole;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class OrderRepositoryTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderItemRepository orderItemRepository;

    @Autowired
    private TestEntityManager entityManager;

    private static long counter=0;

    private User createUser() {

        counter++;

        User user = User.builder()
                .email("user"+counter+"@test.com")
                .passwordHash("hash")
                .firstName("Test")
                .lastName("User")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        return entityManager.persistAndFlush(user);
    }

    private Order createOrder(User user) {

        Order order = Order.builder()
                .orderNumber("ORD-001")
                .user(user)
                .shippingAddressStreet("Street")
                .shippingAddressCity("City")
                .shippingAddressPostalCode("1000")
                .shippingAddressCountry("ETH")
                .build();

        return entityManager.persistAndFlush(order);
    }

    private Product createProduct() {
        return Product.builder()
                .sku("SKU-" + UUID.randomUUID())
                .name("Test Product")
                .description("Test Description")
                .category("Electronics")
                .price(BigDecimal.valueOf(100))
                .quantityInStock(50)
                .createdBy(createUser())
                .build();
    }

    @Test
    void shouldFindByOrderNumber() {

        User user = createUser();
        Order order = createOrder(user);

        Optional<Order> result =
                orderRepository.findByOrderNumber(order.getOrderNumber());

        assertTrue(result.isPresent());
    }

    @Test
    void shouldFindOrdersByUserId() {

        User user = createUser();

        createOrder(user);

        List<Order> orders =
                orderRepository.findByUserId(user.getId());

        assertEquals(1, orders.size());
    }

    @Test
    void shouldFindOrdersByUserIdWithItems() {

        User user = userRepository.save(createUser());

        Order order = Order.builder()
                .orderNumber("ORD-001")
                .user(user)
                .shippingAddressStreet("Street")
                .shippingAddressCity("City")
                .shippingAddressPostalCode("1000")
                .shippingAddressCountry("ET")
                .build();

        order = orderRepository.save(order);

        Product product = productRepository.save(createProduct());

        OrderItem item = OrderItem.builder()
                .order(order)
                .product(product)
                .productName("Laptop")
                .productSku("SKU-001")
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(100))
                .build();

        orderItemRepository.save(item);

        entityManager.flush();
        entityManager.clear();

        List<Order> result =
                orderRepository.findByUserIdWithItems(user.getId());

        assertEquals(1, result.size());

        Order found = result.get(0);

        assertNotNull(found.getItems());

        assertEquals(1, found.getItems().size());
    }

    @Test
    void shouldFindOrdersByStatus() {

        User user = createUser();

        Order order = Order.builder()
                .orderNumber("ORD-100")
                .user(user)
                .orderStatus(OrderStatus.DELIVERED)
                .shippingAddressStreet("Street")
                .shippingAddressCity("City")
                .shippingAddressPostalCode("1000")
                .shippingAddressCountry("ETH")
                .build();

        entityManager.persistAndFlush(order);

        List<Order> orders =
                orderRepository.findByOrderStatus(OrderStatus.DELIVERED);

        assertEquals(1, orders.size());
    }

    @Test
    void shouldFindOrdersByPaymentStatus() {

        User user = createUser();

        Order order = Order.builder()
                .orderNumber("ORD-200")
                .user(user)
                .paymentStatus(PaymentStatus.SUCCESS)
                .shippingAddressStreet("Street")
                .shippingAddressCity("City")
                .shippingAddressPostalCode("1000")
                .shippingAddressCountry("ETH")
                .build();

        entityManager.persistAndFlush(order);

        List<Order> orders =
                orderRepository.findByPaymentStatus(PaymentStatus.SUCCESS);

        assertEquals(1, orders.size());
    }

    @Test
    void shouldCountOrdersByStatus() {

        User user = createUser();

        Order order = createOrder(user);

        order.setOrderStatus(OrderStatus.DELIVERED);

        entityManager.persistAndFlush(order);

        Long count =
                orderRepository.countOrdersByUserAndStatus(
                        user.getId(),
                        OrderStatus.DELIVERED
                );

        assertEquals(1L, count);
    }

    @Test
    void shouldFindOrdersBetweenDates() {

        User user = createUser();

        createOrder(user);

        List<Order> orders =
                orderRepository.findOrdersByDateRange(
                        LocalDateTime.now().minusDays(1),
                        LocalDateTime.now().plusDays(1)
                );

        assertFalse(orders.isEmpty());
    }

    @Test
    void shouldFindStaleOrders() {

        User user = createUser();

        Order order = createOrder(user);

        entityManager.flush();

        List<Order> orders =
                orderRepository.findStaleOrders(
                        OrderStatus.PENDING,
                        LocalDateTime.now().plusHours(1)
                );

        assertFalse(orders.isEmpty());
    }
}