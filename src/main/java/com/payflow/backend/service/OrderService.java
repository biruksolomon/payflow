package com.payflow.backend.service;

import com.payflow.backend.domain.entity.*;
import com.payflow.backend.domain.enums.FulfillmentStatus;
import com.payflow.backend.domain.enums.OrderStatus;
import com.payflow.backend.domain.enums.PaymentStatus;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.OrderRepository;
import com.payflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * OrderService — lifecycle management for orders.
 *
 * Flow:
 *  1. createOrderFromCart  — pulls CartItems, reserves inventory, persists the Order,
 *                            clears the cart, fires ORDER_CREATED notification.
 *  2. confirmOrder         — marks PENDING → PROCESSING after payment succeeds.
 *  3. shipOrder            — marks PROCESSING → SHIPPED, records tracking number.
 *  4. deliverOrder         — marks SHIPPED → DELIVERED.
 *  5. cancelOrder          — PENDING or PROCESSING only; releases reserved inventory.
 *  6. getOrderById         — enforces ownership; users can only see their own orders.
 *  7. getOrdersForUser     — paginated list for the authenticated user.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class OrderService {

    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final CartService cartService;
    private final InventoryService inventoryService;
    private final NotificationService notificationService;

    // ─────────────────────────────────────────────────────────────
    // CREATE ORDER
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates an order from the current cart contents.
     *
     * @param userId          authenticated user
     * @param shippingStreet  shipping address fields (required)
     * @param shippingCity    shipping address fields (required)
     * @param shippingState   shipping address fields
     * @param shippingPostal  shipping address fields (required)
     * @param shippingCountry shipping address fields (required)
     * @param customerNotes   optional free-text notes
     * @return the persisted Order
     */
    @Transactional
    public Order createOrderFromCart(
            Long userId,
            String shippingStreet,
            String shippingCity,
            String shippingState,
            String shippingPostal,
            String shippingCountry,
            String customerNotes) {

        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        Cart cart = cartService.getOrCreateCart(userId);

        if (cart.getItems() == null || cart.getItems().isEmpty()) {
            throw new AuthException("Cart is empty — cannot create an order.", "EMPTY_CART");
        }

        String orderNumber = generateOrderNumber();

        Order order = Order.builder()
                .orderNumber(orderNumber)
                .user(user)
                .orderStatus(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .fulfillmentStatus(FulfillmentStatus.NOT_SHIPPED)
                .shippingAddressStreet(shippingStreet)
                .shippingAddressCity(shippingCity)
                .shippingAddressState(shippingState)
                .shippingAddressPostalCode(shippingPostal)
                .shippingAddressCountry(shippingCountry)
                // Default billing == shipping
                .billingAddressStreet(shippingStreet)
                .billingAddressCity(shippingCity)
                .billingAddressState(shippingState)
                .billingAddressPostalCode(shippingPostal)
                .billingAddressCountry(shippingCountry)
                .customerNotes(customerNotes)
                .currency("USD")
                .build();

        BigDecimal subtotal = BigDecimal.ZERO;

        for (CartItem cartItem : cart.getItems()) {
            Product product = cartItem.getProduct();

            // Reserve inventory (throws if insufficient)
            inventoryService.reserveInventory(product.getId(), cartItem.getQuantity());

            OrderItem orderItem = OrderItem.builder()
                    .order(order)
                    .product(product)
                    .productName(product.getName())
                    .productSku(product.getSku())
                    .quantity(cartItem.getQuantity())
                    .unitPrice(cartItem.getUnitPrice())
                    .build();

            order.addItem(orderItem);
            subtotal = subtotal.add(orderItem.getSubtotal());
        }

        BigDecimal taxAmount    = subtotal.multiply(BigDecimal.valueOf(0.10));
        BigDecimal shippingCost = BigDecimal.valueOf(10.00);
        BigDecimal totalPrice   = subtotal.add(taxAmount).add(shippingCost);

        order.setSubtotal(subtotal);
        order.setTaxAmount(taxAmount);
        order.setShippingCost(shippingCost);
        order.setTotalPrice(totalPrice);

        Order savedOrder = orderRepository.save(order);

        // Clear cart after persisting the order
        cartService.clearCart(userId);

        notificationService.sendOrderCreatedNotification(savedOrder);

        log.info("Order created — orderId={} orderNumber={} userId={} total={}",
                savedOrder.getId(), orderNumber, userId, totalPrice);

        return savedOrder;
    }

    // ─────────────────────────────────────────────────────────────
    // STATUS TRANSITIONS
    // ─────────────────────────────────────────────────────────────

    /**
     * Called internally by PaymentService after payment succeeds.
     * PENDING → PROCESSING, paymentStatus → SUCCESS.
     */
    @Transactional
    public Order confirmOrder(Long orderId) {
        Order order = findOrder(orderId);
        assertCanTransition(order, OrderStatus.PROCESSING);

        order.setOrderStatus(OrderStatus.PROCESSING);
        order.setPaymentStatus(PaymentStatus.SUCCESS);

        Order saved = orderRepository.save(order);
        notificationService.sendOrderConfirmedNotification(saved);

        // Permanently deduct inventory now that payment is confirmed
        for (OrderItem item : saved.getItems()) {
            inventoryService.deductInventory(item.getProduct().getId(), item.getQuantity());
        }

        log.info("Order confirmed — orderId={}", orderId);
        return saved;
    }

    /**
     * Admin / fulfilment operation: PROCESSING → SHIPPED.
     */
    @Transactional
    public Order shipOrder(Long orderId, String trackingNumber) {
        Order order = findOrder(orderId);
        assertCanTransition(order, OrderStatus.SHIPPED);

        order.setOrderStatus(OrderStatus.SHIPPED);
        order.setFulfillmentStatus(FulfillmentStatus.SHIPPED);
        order.setTrackingNumber(trackingNumber);
        order.setShippedAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);
        notificationService.sendOrderShippedNotification(saved);

        log.info("Order shipped — orderId={} tracking={}", orderId, trackingNumber);
        return saved;
    }

    /**
     * Admin / fulfilment operation: SHIPPED → DELIVERED.
     */
    @Transactional
    public Order deliverOrder(Long orderId) {
        Order order = findOrder(orderId);
        assertCanTransition(order, OrderStatus.DELIVERED);

        order.setOrderStatus(OrderStatus.DELIVERED);
        order.setFulfillmentStatus(FulfillmentStatus.DELIVERED);
        order.setDeliveredAt(LocalDateTime.now());

        Order saved = orderRepository.save(order);
        notificationService.sendOrderDeliveredNotification(saved);

        log.info("Order delivered — orderId={}", orderId);
        return saved;
    }

    /**
     * Customer or admin cancellation.  Only PENDING or PROCESSING orders can be cancelled.
     */
    @Transactional
    public Order cancelOrder(Long orderId, Long requestingUserId, boolean isAdmin) {
        Order order = findOrderWithItems(orderId);

        if (!isAdmin && !order.getUser().getId().equals(requestingUserId)) {
            throw new AuthException("Not authorized to cancel this order", "FORBIDDEN");
        }

        if (!order.isPending() && !order.isProcessing()) {
            throw new AuthException(
                    "Order cannot be cancelled in status: " + order.getOrderStatus(),
                    "INVALID_STATE_TRANSITION");
        }

        order.setOrderStatus(OrderStatus.CANCELLED);
        order.setCancelledAt(LocalDateTime.now());

        // Release the previously reserved inventory
        for (OrderItem item : order.getItems()) {
            inventoryService.releaseReservedInventory(item.getProduct().getId(), item.getQuantity());
        }

        Order saved = orderRepository.save(order);
        notificationService.sendOrderCancelledNotification(saved);

        log.info("Order cancelled — orderId={} by userId={}", orderId, requestingUserId);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Order getOrderById(Long orderId, Long userId, boolean isAdmin) {
        Order order = findOrderWithItems(orderId);
        if (!isAdmin && !order.getUser().getId().equals(userId)) {
            throw new AuthException("Not authorized to view this order", "FORBIDDEN");
        }
        return order;
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersForUser(Long userId) {
        return orderRepository.findByUserIdWithItems(userId);
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByStatus(OrderStatus status) {
        return orderRepository.findByOrderStatus(status);
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new AuthException("Order not found: " + orderId, "ORDER_NOT_FOUND"));
    }

    private Order findOrderWithItems(Long orderId) {
        return orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new AuthException("Order not found: " + orderId, "ORDER_NOT_FOUND"));
    }

    private void assertCanTransition(Order order, OrderStatus target) {
        if (!order.getOrderStatus().canTransitionTo(target)) {
            throw new AuthException(
                    "Cannot transition order from " + order.getOrderStatus() + " to " + target,
                    "INVALID_STATE_TRANSITION");
        }
    }

    private String generateOrderNumber() {
        int year = LocalDateTime.now().getYear();
        long fragment = System.currentTimeMillis() % 1_000_000L;
        return String.format("ORD-%d-%06d", year, fragment);
    }
}