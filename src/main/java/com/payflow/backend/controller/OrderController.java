package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.enums.OrderStatus;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);

        Order order = orderService.createOrderFromCart(
                userId,
                (String) body.get("shippingStreet"),
                (String) body.get("shippingCity"),
                (String) body.get("shippingState"),
                (String) body.get("shippingPostal"),
                (String) body.get("shippingCountry"),
                (String) body.get("customerNotes"));

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
        boolean isAdmin = userDetails.isAdmin();
        Order order = orderService.getOrderById(id, userDetails.getId(), isAdmin);
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
        boolean isAdmin = userDetails.isAdmin();
        Order order = orderService.cancelOrder(id, userDetails.getId(), isAdmin);
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
            @RequestBody(required = false) Map<String, Object> body) {

        String trackingNumber = body != null ? (String) body.get("trackingNumber") : null;
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
