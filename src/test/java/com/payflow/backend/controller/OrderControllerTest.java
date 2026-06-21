package com.payflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.config.SecurityConfig;
import com.payflow.backend.config.TestWebMvcSecurityConfig;
import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.FulfillmentStatus;
import com.payflow.backend.domain.enums.OrderStatus;
import com.payflow.backend.domain.enums.PaymentStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.exception.AuthException;
import org.springframework.http.HttpStatus;
import com.payflow.backend.security.JwtAuthenticationFilter;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.OrderService;
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
        controllers = OrderController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestWebMvcSecurityConfig.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    private Order order;
    private PayFlowUserDetails customerDetails;
    private UsernamePasswordAuthenticationToken customerToken;
    private PayFlowUserDetails adminDetails;
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
        customerDetails = new PayFlowUserDetails(customer);
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
        adminDetails = new PayFlowUserDetails(admin);
        adminToken = new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());

        order = Order.builder()
                .id(1L)
                .orderNumber("ORD-2026-000001")
                .user(customer)
                .orderStatus(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .fulfillmentStatus(FulfillmentStatus.NOT_SHIPPED)
                .subtotal(new BigDecimal("100.00"))
                .taxAmount(new BigDecimal("10.00"))
                .shippingCost(new BigDecimal("10.00"))
                .totalPrice(new BigDecimal("120.00"))
                .shippingAddressStreet("123 Main St")
                .shippingAddressCity("Springfield")
                .shippingAddressPostalCode("12345")
                .shippingAddressCountry("US")
                .items(new ArrayList<>())
                .build();
    }

    // ─────────────────────────────────────────────────��───────────
    // CREATE ORDER
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldCreateOrderFromCart() throws Exception {
        when(orderService.createOrderFromCart(eq(1L), any(), any(), any(), any(), any(), any()))
                .thenReturn(order);

        Map<String, Object> body = Map.of(
                "shippingStreet", "123 Main St",
                "shippingCity", "Springfield",
                "shippingState", "IL",
                "shippingPostal", "12345",
                "shippingCountry", "US"
        );

        mockMvc.perform(post("/api/orders")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderNumber").value("ORD-2026-000001"))
                .andExpect(jsonPath("$.orderStatus").value("PENDING"));

        verify(orderService).createOrderFromCart(eq(1L), any(), any(), any(), any(), any(), any());
    }

    @Test
    void shouldReturn400WhenCartIsEmpty() throws Exception {
        when(orderService.createOrderFromCart(eq(1L), any(), any(), any(), any(), any(), any()))
                .thenThrow(new AuthException("Cart is empty", "EMPTY_CART", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/orders")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("shippingStreet", "123 Main St"))))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────
    // GET MY ORDERS
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnOrdersForCurrentUser() throws Exception {
        when(orderService.getOrdersForUser(1L)).thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders")
                        .with(authentication(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderNumber").value("ORD-2026-000001"));

        verify(orderService).getOrdersForUser(1L);
    }

    @Test
    void shouldReturnEmptyListWhenNoOrders() throws Exception {
        when(orderService.getOrdersForUser(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/orders")
                        .with(authentication(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─────────────────────────────────────────────────────────────
    // GET ORDER BY ID
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnOrderByIdForOwner() throws Exception {
        when(orderService.getOrderById(1L, 1L, false)).thenReturn(order);

        mockMvc.perform(get("/api/orders/1")
                        .with(authentication(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.orderNumber").value("ORD-2026-000001"));

        verify(orderService).getOrderById(1L, 1L, false);
    }

    @Test
    void shouldReturn403WhenCustomerAccessesOtherOrder() throws Exception {
        when(orderService.getOrderById(5L, 1L, false))
                .thenThrow(new AuthException("Not authorized to view this order", "FORBIDDEN", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/orders/5")
                        .with(authentication(customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldAllowAdminToAccessAnyOrder() throws Exception {
        when(orderService.getOrderById(1L, 99L, true)).thenReturn(order);

        mockMvc.perform(get("/api/orders/1")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk());

        verify(orderService).getOrderById(1L, 99L, true);
    }

    // ─────────────────────────────────────────────────────────────
    // CANCEL ORDER
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldCancelOrderSuccessfully() throws Exception {
        Order cancelled = Order.builder().id(1L).orderNumber("ORD-2026-000001")
                .orderStatus(OrderStatus.CANCELLED).items(new ArrayList<>()).build();
        when(orderService.cancelOrder(1L, 1L, false)).thenReturn(cancelled);

        mockMvc.perform(post("/api/orders/1/cancel")
                        .with(authentication(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("CANCELLED"));

        verify(orderService).cancelOrder(1L, 1L, false);
    }

    @Test
    void shouldReturn400WhenCancellingNonCancellableOrder() throws Exception {
        when(orderService.cancelOrder(1L, 1L, false))
                .thenThrow(new AuthException("Order cannot be cancelled", "INVALID_STATE_TRANSITION", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/orders/1/cancel")
                        .with(authentication(customerToken)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────
    // SHIP ORDER (admin)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldShipOrderSuccessfully() throws Exception {
        Order shipped = Order.builder().id(1L).orderNumber("ORD-2026-000001")
                .orderStatus(OrderStatus.SHIPPED).trackingNumber("TRACK-123").items(new ArrayList<>()).build();
        when(orderService.shipOrder(1L, "TRACK-123")).thenReturn(shipped);

        mockMvc.perform(post("/api/orders/1/ship")
                        .with(authentication(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("trackingNumber", "TRACK-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("SHIPPED"))
                .andExpect(jsonPath("$.trackingNumber").value("TRACK-123"));

        verify(orderService).shipOrder(1L, "TRACK-123");
    }

    // ─────────────────────────────────────────────────────────────
    // DELIVER ORDER (admin)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldDeliverOrderSuccessfully() throws Exception {
        Order delivered = Order.builder().id(1L).orderNumber("ORD-2026-000001")
                .orderStatus(OrderStatus.DELIVERED).items(new ArrayList<>()).build();
        when(orderService.deliverOrder(1L)).thenReturn(delivered);

        mockMvc.perform(post("/api/orders/1/deliver")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderStatus").value("DELIVERED"));

        verify(orderService).deliverOrder(1L);
    }

    // ─────────────────────────────────────────────────────────────
    // ORDERS BY STATUS (admin)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnOrdersByStatus() throws Exception {
        when(orderService.getOrdersByStatus(OrderStatus.PENDING)).thenReturn(List.of(order));

        mockMvc.perform(get("/api/orders/admin/by-status")
                        .param("status", "PENDING")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].orderStatus").value("PENDING"));

        verify(orderService).getOrdersByStatus(OrderStatus.PENDING);
    }
}