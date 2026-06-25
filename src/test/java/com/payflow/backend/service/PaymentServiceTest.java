package com.payflow.backend.service;

import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.domain.enums.PaymentStatus;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.OrderRepository;
import com.payflow.backend.repository.PaymentRepository;
import com.payflow.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private OrderService orderService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private PaymentService paymentService;

    private User user;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(1L)
                .email("test@test.com")
                .build();

        order = Order.builder()
                .id(10L)
                .user(user)
                .totalPrice(BigDecimal.valueOf(250))
                .build();

        payment = Payment.builder()
                .id(100L)
                .order(order)
                .user(user)
                .amount(BigDecimal.valueOf(250))
                .paymentStatus(PaymentStatus.PENDING)
                .retryCount(0)
                .currency(Currency.USD)
                .transactionId("TXN-123")
                .build();
    }

    // ==========================================================
    // INITIATE PAYMENT
    // ==========================================================

    @Test
    void initiatePayment_ShouldCreatePayment() {

        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(paymentRepository.findByOrderId(10L))
                .thenReturn(Optional.empty());

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.initiatePayment(
                10L,
                1L,
                PaymentMethod.CREDIT_CARD,
                "pi_123",
                "4242",
                "VISA"
        );

        assertThat(result).isNotNull();
        assertThat(result.getPaymentStatus())
                .isEqualTo(PaymentStatus.PENDING);

        assertThat(result.getAmount())
                .isEqualByComparingTo(BigDecimal.valueOf(250));

        verify(paymentRepository).save(any(Payment.class));
    }

    @Test
    void initiatePayment_ShouldThrow_WhenOrderNotFound() {

        when(orderRepository.findById(10L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.initiatePayment(
                        10L,
                        1L,
                        PaymentMethod.CREDIT_CARD,
                        null,
                        null,
                        null))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void initiatePayment_ShouldThrow_WhenUserNotFound() {

        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.initiatePayment(
                        10L,
                        1L,
                        PaymentMethod.CREDIT_CARD,
                        null,
                        null,
                        null))
                .isInstanceOf(UserNotFoundException.class);
    }

    @Test
    void initiatePayment_ShouldThrow_WhenUserDoesNotOwnOrder() {

        User anotherUser = User.builder()
                .id(99L)
                .build();

        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        when(userRepository.findActiveById(99L))
                .thenReturn(Optional.of(anotherUser));

        assertThatThrownBy(() ->
                paymentService.initiatePayment(
                        10L,
                        99L,
                        PaymentMethod.CREDIT_CARD,
                        null,
                        null,
                        null))
                .isInstanceOf(AuthException.class);
    }

    @Test
    void initiatePayment_ShouldThrow_WhenAlreadyPaid() {

        Payment paidPayment = Payment.builder()
                .paymentStatus(PaymentStatus.SUCCESS)
                .build();

        when(orderRepository.findById(10L))
                .thenReturn(Optional.of(order));

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(paymentRepository.findByOrderId(10L))
                .thenReturn(Optional.of(paidPayment));

        assertThatThrownBy(() ->
                paymentService.initiatePayment(
                        10L,
                        1L,
                        PaymentMethod.CREDIT_CARD,
                        null,
                        null,
                        null))
                .isInstanceOf(AuthException.class);
    }

    // ==========================================================
    // SUCCESS PAYMENT
    // ==========================================================

    @Test
    void recordSuccessfulPayment_ShouldMarkSuccess() {

        when(paymentRepository.findById(100L))
                .thenReturn(Optional.of(payment));

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(i -> i.getArgument(0));

        Payment result =
                paymentService.recordSuccessfulPayment(100L);

        assertThat(result.getPaymentStatus())
                .isEqualTo(PaymentStatus.SUCCESS);

        assertThat(result.getProcessedAt())
                .isNotNull();

        verify(orderService)
                .confirmOrder(order.getId());

        verify(notificationService)
                .sendPaymentSuccessNotification(any(Payment.class));
    }

    @Test
    void recordSuccessfulPayment_ShouldReturnExisting_WhenAlreadySuccess() {

        payment.setPaymentStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findById(100L))
                .thenReturn(Optional.of(payment));

        Payment result =
                paymentService.recordSuccessfulPayment(100L);

        assertThat(result.getPaymentStatus())
                .isEqualTo(PaymentStatus.SUCCESS);

        verify(orderService, never())
                .confirmOrder(anyLong());
    }

    // ==========================================================
    // FAILED PAYMENT
    // ==========================================================

    @Test
    void recordFailedPayment_ShouldUpdateFailureInfo() {

        when(paymentRepository.findById(100L))
                .thenReturn(Optional.of(payment));

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(i -> i.getArgument(0));

        Payment result =
                paymentService.recordFailedPayment(
                        100L,
                        "CARD_DECLINED",
                        "Card was declined");

        assertThat(result.getPaymentStatus())
                .isEqualTo(PaymentStatus.FAILED);

        assertThat(result.getErrorCode())
                .isEqualTo("CARD_DECLINED");

        assertThat(result.getRetryCount())
                .isEqualTo(1);

        verify(notificationService)
                .sendPaymentFailedNotification(any(Payment.class));
    }

    // ==========================================================
    // REFUND
    // ==========================================================

    @Test
    void initiateRefund_ShouldPerformFullRefund() {

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setRefundedAmount(BigDecimal.ZERO);

        when(paymentRepository.findByOrderId(10L))
                .thenReturn(Optional.of(payment));

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(i -> i.getArgument(0));

        Payment result =
                paymentService.initiateRefund(
                        10L,
                        1L,
                        false,
                        null);

        assertThat(result.getPaymentStatus())
                .isEqualTo(PaymentStatus.REFUNDED);

        verify(notificationService)
                .sendRefundInitiatedNotification(any(Payment.class));
    }

    @Test
    void initiateRefund_ShouldPerformPartialRefund() {

        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setRefundedAmount(BigDecimal.ZERO);

        when(paymentRepository.findByOrderId(10L))
                .thenReturn(Optional.of(payment));

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(i -> i.getArgument(0));

        Payment result =
                paymentService.initiateRefund(
                        10L,
                        1L,
                        false,
                        BigDecimal.valueOf(50));

        assertThat(result.getPaymentStatus())
                .isEqualTo(PaymentStatus.PARTIALLY_REFUNDED);
    }

    @Test
    void initiateRefund_ShouldThrow_WhenUnauthorized() {

        payment.setPaymentStatus(PaymentStatus.SUCCESS);

        when(paymentRepository.findByOrderId(10L))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() ->
                paymentService.initiateRefund(
                        10L,
                        999L,
                        false,
                        BigDecimal.TEN))
                .isInstanceOf(AuthException.class);
    }

    // ==========================================================
    // COMPLETE REFUND
    // ==========================================================

    @Test
    void completeRefund_ShouldSetRefundedAt() {

        when(paymentRepository.findById(100L))
                .thenReturn(Optional.of(payment));

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(i -> i.getArgument(0));

        Payment result =
                paymentService.completeRefund(100L);

        assertThat(result.getRefundedAt())
                .isNotNull();

        verify(notificationService)
                .sendRefundCompletedNotification(any(Payment.class));
    }

    // ==========================================================
    // GET PAYMENT BY ORDER
    // ==========================================================

    @Test
    void getPaymentByOrderId_ShouldReturnPayment() {

        when(paymentRepository.findByOrderId(10L))
                .thenReturn(Optional.of(payment));

        Payment result =
                paymentService.getPaymentByOrderId(
                        10L,
                        1L,
                        false);

        assertThat(result).isEqualTo(payment);
    }

    @Test
    void getPaymentByOrderId_ShouldThrow_WhenUnauthorized() {

        when(paymentRepository.findByOrderId(10L))
                .thenReturn(Optional.of(payment));

        assertThatThrownBy(() ->
                paymentService.getPaymentByOrderId(
                        10L,
                        999L,
                        false))
                .isInstanceOf(AuthException.class);
    }

    // ==========================================================
    // GET PAYMENTS FOR USER
    // ==========================================================

    @Test
    void getPaymentsForUser_ShouldReturnList() {

        when(paymentRepository.findByUserId(1L))
                .thenReturn(List.of(payment));

        List<Payment> result =
                paymentService.getPaymentsForUser(1L);

        assertThat(result).hasSize(1);
    }

    // ==========================================================
    // GET BY TRANSACTION ID
    // ==========================================================

    @Test
    void getPaymentByTransactionId_ShouldReturnPayment() {

        when(paymentRepository.findByTransactionId("TXN-123"))
                .thenReturn(Optional.of(payment));

        Payment result =
                paymentService.getPaymentByTransactionId("TXN-123");

        assertThat(result).isEqualTo(payment);
    }

    @Test
    void getPaymentByTransactionId_ShouldThrow_WhenNotFound() {

        when(paymentRepository.findByTransactionId("TXN-404"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.getPaymentByTransactionId("TXN-404"))
                .isInstanceOf(AuthException.class);
    }

    // ==========================================================
    // UPDATE STRIPE INTENT ID
    // ==========================================================

    @Test
    void updateStripeIntentId_ShouldPersistIntentIdOnPayment() {

        when(paymentRepository.findById(100L))
                .thenReturn(Optional.of(payment));

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.updateStripeIntentId(100L, "pi_stripe_abc");

        assertThat(result.getStripePaymentIntentId()).isEqualTo("pi_stripe_abc");
        verify(paymentRepository).save(payment);
    }

    @Test
    void updateStripeIntentId_ShouldThrow_WhenPaymentNotFound() {

        when(paymentRepository.findById(999L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.updateStripeIntentId(999L, "pi_any"))
                .isInstanceOf(AuthException.class);

        verify(paymentRepository, never()).save(any());
    }

    // ==========================================================
    // COMPLETE REFUND BY INTENT ID
    // ==========================================================

    @Test
    void completeRefundByIntentId_ShouldDelegateToCompleteRefund() {

        // completeRefund internally calls findById; findByStripePaymentIntentId provides the id
        when(paymentRepository.findByStripePaymentIntentId("pi_original"))
                .thenReturn(Optional.of(payment));

        when(paymentRepository.findById(100L))
                .thenReturn(Optional.of(payment));

        when(paymentRepository.save(any(Payment.class)))
                .thenAnswer(i -> i.getArgument(0));

        Payment result = paymentService.completeRefundByIntentId("pi_original");

        assertThat(result.getRefundedAt()).isNotNull();
        verify(notificationService).sendRefundCompletedNotification(any(Payment.class));
    }

    @Test
    void completeRefundByIntentId_ShouldThrow_WhenIntentIdNotFound() {

        when(paymentRepository.findByStripePaymentIntentId("pi_unknown"))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                paymentService.completeRefundByIntentId("pi_unknown"))
                .isInstanceOf(AuthException.class)
                .hasMessageContaining("Payment not found for Stripe intent");
    }
}
