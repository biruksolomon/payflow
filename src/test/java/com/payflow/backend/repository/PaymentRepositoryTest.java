package com.payflow.backend.repository;

import com.payflow.backend.domain.entity.*;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.domain.enums.PaymentStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class PaymentRepositoryTest {

    @Autowired
    private PaymentRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    private Payment createPayment() {

        User user = User.builder()
                .email("payment@test.com")
                .passwordHash("hash")
                .firstName("John")
                .lastName("Doe")
                .build();

        entityManager.persist(user);

        Order order = Order.builder()
                .orderNumber("ORD-PAY")
                .user(user)
                .shippingAddressStreet("Street")
                .shippingAddressCity("City")
                .shippingAddressPostalCode("1000")
                .shippingAddressCountry("ETH")
                .build();

        entityManager.persist(order);

        Payment payment = Payment.builder()
                .user(user)
                .order(order)
                .paymentMethod(PaymentMethod.STRIPE)
                .paymentStatus(PaymentStatus.SUCCESS)
                .amount(BigDecimal.valueOf(100))
                .transactionId("TXN-1")
                .stripePaymentIntentId("pi_123")
                .processedAt(LocalDateTime.now())
                .build();

        return entityManager.persistAndFlush(payment);
    }

    @Test
    void shouldFindByOrderId() {

        Payment payment = createPayment();

        Optional<Payment> result =
                repository.findByOrderId(
                        payment.getOrder().getId()
                );

        assertTrue(result.isPresent());
    }

    @Test
    void shouldFindByTransactionId() {

        createPayment();

        assertTrue(
                repository.findByTransactionId("TXN-1")
                        .isPresent()
        );
    }

    @Test
    void shouldFindByStripeIntent() {

        createPayment();

        assertTrue(
                repository.findByStripePaymentIntentId("pi_123")
                        .isPresent()
        );
    }

    @Test
    void shouldFindByUserId() {

        Payment payment = createPayment();

        List<Payment> payments =
                repository.findByUserId(
                        payment.getUser().getId()
                );

        assertEquals(1, payments.size());
    }

    @Test
    void shouldFindByStatus() {

        createPayment();

        List<Payment> payments =
                repository.findByPaymentStatus(
                        PaymentStatus.SUCCESS
                );

        assertEquals(1, payments.size());
    }

    @Test
    void shouldFindPaymentsForRetry() {

        User user = User.builder()
                .email("retry@test.com")
                .passwordHash("hash")
                .firstName("Retry")
                .lastName("User")
                .build();

        entityManager.persist(user);

        Order order = Order.builder()
                .orderNumber("ORD-RETRY")
                .user(user)
                .shippingAddressStreet("Street")
                .shippingAddressCity("City")
                .shippingAddressPostalCode("1000")
                .shippingAddressCountry("ETH")
                .build();

        entityManager.persist(order);

        Payment payment = Payment.builder()
                .user(user)
                .order(order)
                .paymentMethod(PaymentMethod.STRIPE)
                .paymentStatus(PaymentStatus.PENDING)
                .amount(BigDecimal.valueOf(100))
                .retryCount(1)
                .build();

        entityManager.persistAndFlush(payment);

        List<Payment> payments =
                repository.findPaymentsForRetry(
                        PaymentStatus.PENDING,
                        LocalDateTime.now().plusHours(1),
                        3
                );

        assertFalse(payments.isEmpty());
    }

    @Test
    void shouldCalculateSuccessfulPaymentTotal() {

        createPayment();

        Optional<BigDecimal> total =
                repository.getTotalSuccessfulPayments(
                        PaymentStatus.SUCCESS,
                        LocalDateTime.now().minusDays(1),
                        LocalDateTime.now().plusDays(1)
                );

        assertTrue(total.isPresent());
        assertEquals(
                0,
                BigDecimal.valueOf(100)
                        .compareTo(total.get())
        );
    }
}