package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.enums.OrderStatus;
import com.payflow.backend.dto.request.CreateOrderRequest;
import com.payflow.backend.dto.request.ShipOrderRequest;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
@Tag(name = "Orders", description = "Order lifecycle management")
@SecurityRequirement(name = "Bearer Authentication")
public class OrderController {

    private final OrderService orderService;

    // ─────────────────────────────────────────────────────────────
    // CREATE ORDER FROM CART
    // ─────────────────────────────────────────────────────────────

    @PostMapping
    @Operation(summary = "Create an order from the current cart")
    public ResponseEntity<Order> createOrder(
            @Valid @RequestBody CreateOrderRequest request,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);

        Order order = orderService.createOrderFromCart(
                userId,
                request.getShippingStreet(),
                request.getShippingCity(),
                request.getShippingState(),
                request.getShippingPostal(),
                request.getShippingCountry(),
                request.getCustomerNotes());

        log.info("Order created — orderId={} userId={}", order.getId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(order);
    }

    // ─────────────────────────────────────────────────────────────
    // GET ORDERS (current user)
    // ─────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get all orders for the authenticated user")
    public ResponseEntity<List<Order>> getMyOrders(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return ResponseEntity.ok(orderService.getOrdersForUser(userId));
    }

    // ─────────────────────────────────────────────────────────────
    // GET ORDER BY ID
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID (users can only view their own orders)")
    public ResponseEntity<Order> getOrderById(
            @PathVariable Long id,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);
        Order order = orderService.getOrderById(id, userDetails.getId(), userDetails.hasAdminPrivileges());
        return ResponseEntity.ok(order);
    }

    // ─────────────────────────────────────────────────────────────
    // CANCEL ORDER
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a pending or processing order")
    public ResponseEntity<Order> cancelOrder(
            @PathVariable Long id,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);
        Order order = orderService.cancelOrder(id, userDetails.getId(), userDetails.hasAdminPrivileges());
        log.info("Order cancelled — orderId={} userId={}", id, userDetails.getId());
        return ResponseEntity.ok(order);
    }

    // ─────────────────────────────────────────────────────────────
    // ADMIN — status transitions
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/ship")
    @Operation(summary = "Mark order as shipped (admin only)")
    public ResponseEntity<Order> shipOrder(
            @PathVariable Long id,
            @Valid @RequestBody(required = false) ShipOrderRequest request) {

        String trackingNumber = request != null ? request.getTrackingNumber() : null;
        Order order = orderService.shipOrder(id, trackingNumber);
        log.info("Order shipped — orderId={}", id);
        return ResponseEntity.ok(order);
    }

    @PostMapping("/{id}/deliver")
    @Operation(summary = "Mark order as delivered (admin only)")
    public ResponseEntity<Order> deliverOrder(@PathVariable Long id) {
        Order order = orderService.deliverOrder(id);
        log.info("Order delivered — orderId={}", id);
        return ResponseEntity.ok(order);
    }

    @GetMapping("/admin/by-status")
    @Operation(summary = "List orders by status (admin only)")
    public ResponseEntity<List<Order>> getOrdersByStatus(@RequestParam OrderStatus status) {
        return ResponseEntity.ok(orderService.getOrdersByStatus(status));
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private PayFlowUserDetails resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required", "UNAUTHORIZED");
        }
        return (PayFlowUserDetails) authentication.getPrincipal();
    }

    private Long resolveUserId(Authentication authentication) {
        return resolveUser(authentication).getId();
    }
}
