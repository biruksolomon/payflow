package com.payflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.config.SecurityConfig;
import com.payflow.backend.config.TestWebMvcSecurityConfig;
import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.*;
import com.payflow.backend.exception.AuthException;
import org.springframework.http.HttpStatus;
import com.payflow.backend.security.JwtAuthenticationFilter;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.PaymentService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = PaymentController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestWebMvcSecurityConfig.class)
class PaymentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private PaymentService paymentService;

    private Payment payment;
    private UsernamePasswordAuthenticationToken customerToken;
    private UsernamePasswordAuthenticationToken adminToken;

    @BeforeEach
    void setUp() {
        User customer = User.builder()
                .id(1L)
                .email("customer@test.com")
                .passwordHash("hashed")
                .firstName("John")
                .lastName("Doe")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
        PayFlowUserDetails customerDetails = new PayFlowUserDetails(customer);
        customerToken = new UsernamePasswordAuthenticationToken(customerDetails, null, customerDetails.getAuthorities());

        User admin = User.builder()
                .id(99L)
                .email("admin@test.com")
                .passwordHash("hashed")
                .firstName("Admin")
                .lastName("User")
                .userRole(UserRole.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
        PayFlowUserDetails adminDetails = new PayFlowUserDetails(admin);
        adminToken = new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());

        Order order = Order.builder()
                .id(1L)
                .orderNumber("ORD-2026-000001")
                .user(customer)
                .orderStatus(OrderStatus.PENDING)
                .totalPrice(new BigDecimal("120.00"))
                .items(new ArrayList<>())
                .build();

        payment = Payment.builder()
                .id(1L)
                .order(order)
                .user(customer)
                .paymentMethod(PaymentMethod.CREDIT_CARD)
                .paymentStatus(PaymentStatus.PENDING)
                .amount(new BigDecimal("120.00"))
                .refundedAmount(BigDecimal.ZERO)
                .currency(Currency.USD)
                .transactionId("TXN-ABCDEF123456")
                .retryCount(0)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // INITIATE PAYMENT
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldInitiatePaymentSuccessfully() throws Exception {
        when(paymentService.initiatePayment(eq(1L), eq(1L), eq(PaymentMethod.CREDIT_CARD), any(), any(), any()))
                .thenReturn(payment);

        mockMvc.perform(post("/api/payments")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", 1,
                                "paymentMethod", "CREDIT_CARD"
                        ))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.paymentStatus").value("PENDING"));

        verify(paymentService).initiatePayment(eq(1L), eq(1L), eq(PaymentMethod.CREDIT_CARD), any(), any(), any());
    }

    @Test
    void shouldReturn400WhenOrderIdMissing() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("paymentMethod", "CREDIT_CARD"))))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).initiatePayment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturn400WhenPaymentMethodInvalid() throws Exception {
        mockMvc.perform(post("/api/payments")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", 1,
                                "paymentMethod", "INVALID_METHOD"
                        ))))
                .andExpect(status().isBadRequest());

        verify(paymentService, never()).initiatePayment(any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturn400WhenOrderAlreadyPaid() throws Exception {
        when(paymentService.initiatePayment(eq(1L), eq(1L), any(), any(), any(), any()))
                .thenThrow(new AuthException("Order is already paid: 1", "ALREADY_PAID", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/payments")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "orderId", 1,
                                "paymentMethod", "CREDIT_CARD"
                        ))))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────
    // RECORD SUCCESS
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldRecordPaymentSuccess() throws Exception {
        Payment successPayment = Payment.builder().id(1L).paymentStatus(PaymentStatus.SUCCESS)
                .amount(new BigDecimal("120.00")).refundedAmount(BigDecimal.ZERO)
                .currency(Currency.USD).retryCount(0).build();
        when(paymentService.recordSuccessfulPayment(1L)).thenReturn(successPayment);

        mockMvc.perform(post("/api/payments/1/success")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("SUCCESS"));

        verify(paymentService).recordSuccessfulPayment(1L);
    }

    // ─────────────────────────────────────────────────────────────
    // RECORD FAILURE
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldRecordPaymentFailure() throws Exception {
        Payment failedPayment = Payment.builder().id(1L).paymentStatus(PaymentStatus.FAILED)
                .errorCode("CARD_DECLINED").amount(new BigDecimal("120.00"))
                .refundedAmount(BigDecimal.ZERO).currency(Currency.USD).retryCount(1).build();
        when(paymentService.recordFailedPayment(1L, "CARD_DECLINED", "Card was declined"))
                .thenReturn(failedPayment);

        mockMvc.perform(post("/api/payments/1/failure")
                        .with(authentication(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "errorCode", "CARD_DECLINED",
                                "errorMessage", "Card was declined"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("FAILED"))
                .andExpect(jsonPath("$.errorCode").value("CARD_DECLINED"));

        verify(paymentService).recordFailedPayment(1L, "CARD_DECLINED", "Card was declined");
    }

    // ─────────────────────────────────────────────────────────────
    // INITIATE REFUND
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldInitiateRefundSuccessfully() throws Exception {
        Payment refundedPayment = Payment.builder().id(1L).paymentStatus(PaymentStatus.REFUNDED)
                .amount(new BigDecimal("120.00")).refundedAmount(new BigDecimal("120.00"))
                .currency(Currency.USD).retryCount(0).build();
        when(paymentService.initiateRefund(eq(1L), eq(1L), eq(false), any()))
                .thenReturn(refundedPayment);

        mockMvc.perform(post("/api/payments/orders/1/refund")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("refundAmount", 120.00))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));

        verify(paymentService).initiateRefund(eq(1L), eq(1L), eq(false), any());
    }

    @Test
    void shouldInitiateFullRefundWithNoBody() throws Exception {
        Payment refundedPayment = Payment.builder().id(1L).paymentStatus(PaymentStatus.REFUNDED)
                .amount(new BigDecimal("120.00")).refundedAmount(new BigDecimal("120.00"))
                .currency(Currency.USD).retryCount(0).build();
        when(paymentService.initiateRefund(eq(1L), eq(1L), eq(false), isNull()))
                .thenReturn(refundedPayment);

        mockMvc.perform(post("/api/payments/orders/1/refund")
                        .with(authentication(customerToken)))
                .andExpect(status().isOk());

        verify(paymentService).initiateRefund(eq(1L), eq(1L), eq(false), isNull());
    }

    // ─────────────────────────────────────────────────────────────
    // COMPLETE REFUND
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldCompleteRefund() throws Exception {
        Payment completed = Payment.builder().id(1L).paymentStatus(PaymentStatus.REFUNDED)
                .amount(new BigDecimal("120.00")).refundedAmount(new BigDecimal("120.00"))
                .currency(Currency.USD).retryCount(0).build();
        when(paymentService.completeRefund(1L)).thenReturn(completed);

        mockMvc.perform(post("/api/payments/1/complete-refund")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentStatus").value("REFUNDED"));

        verify(paymentService).completeRefund(1L);
    }

    // ─────────────────────────────────────────────────────────────
    // GET MY PAYMENTS
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnPaymentsForCurrentUser() throws Exception {
        when(paymentService.getPaymentsForUser(1L)).thenReturn(List.of(payment));

        mockMvc.perform(get("/api/payments/my")
                        .with(authentication(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1));

        verify(paymentService).getPaymentsForUser(1L);
    }

    // ─────────────────────────────────────────────────────────────
    // GET BY ORDER
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnPaymentByOrderId() throws Exception {
        when(paymentService.getPaymentByOrderId(1L, 1L, false)).thenReturn(payment);

        mockMvc.perform(get("/api/payments/orders/1")
                        .with(authentication(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-ABCDEF123456"));

        verify(paymentService).getPaymentByOrderId(1L, 1L, false);
    }

    @Test
    void shouldReturn403WhenCustomerAccessesOtherPayment() throws Exception {
        when(paymentService.getPaymentByOrderId(99L, 1L, false))
                .thenThrow(new AuthException("Not authorized to view this payment", "FORBIDDEN", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/payments/orders/99")
                        .with(authentication(customerToken)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────
    // GET BY TRANSACTION ID
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnPaymentByTransactionId() throws Exception {
        when(paymentService.getPaymentByTransactionId("TXN-ABCDEF123456")).thenReturn(payment);

        mockMvc.perform(get("/api/payments/transaction/TXN-ABCDEF123456")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.transactionId").value("TXN-ABCDEF123456"));

        verify(paymentService).getPaymentByTransactionId("TXN-ABCDEF123456");
    }

    @Test
    void shouldReturn404WhenTransactionNotFound() throws Exception {
        when(paymentService.getPaymentByTransactionId("UNKNOWN"))
                .thenThrow(new AuthException("Payment not found: UNKNOWN", "PAYMENT_NOT_FOUND", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/payments/transaction/UNKNOWN")
                        .with(authentication(adminToken)))
                .andExpect(status().isNotFound());
    }
}
