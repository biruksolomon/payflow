package com.payflow.backend.service;

import com.payflow.backend.domain.entity.*;
import com.payflow.backend.domain.enums.OrderStatus;
import com.payflow.backend.domain.enums.PaymentStatus;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.OrderRepository;
import com.payflow.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CartService cartService;

    @Mock
    private InventoryService inventoryService;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private OrderService orderService;

    private User user;
    private Product product;
    private Cart cart;
    private Order order;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(1L)
                .build();

        product = Product.builder()
                .id(100L)
                .sku("SKU-001")
                .name("Laptop")
                .price(BigDecimal.valueOf(1000))
                .quantityInStock(50)
                .build();

        CartItem cartItem = CartItem.builder()
                .id(1L)
                .product(product)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(1000))
                .build();

        cart = Cart.builder()
                .id(1L)
                .user(user)
                .items(new ArrayList<>(List.of(cartItem)))
                .build();

        OrderItem orderItem = OrderItem.builder()
                .id(1L)
                .product(product)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(1000))
                .build();

        order = Order.builder()
                .id(1L)
                .orderNumber("ORD-2025-000001")
                .user(user)
                .orderStatus(OrderStatus.PENDING)
                .paymentStatus(PaymentStatus.PENDING)
                .items(new ArrayList<>(List.of(orderItem)))
                .build();
    }

    @Test
    void createOrderFromCart_Success() {

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(cartService.getOrCreateCart(1L))
                .thenReturn(cart);

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.createOrderFromCart(
                1L,
                "Street",
                "City",
                "State",
                "12345",
                "USA",
                "note"
        );

        assertNotNull(result);
        assertEquals(OrderStatus.PENDING, result.getOrderStatus());

        verify(inventoryService)
                .reserveInventory(product.getId(), 2);

        verify(cartService)
                .clearCart(1L);

        verify(notificationService)
                .sendOrderCreatedNotification(any(Order.class));
    }

    @Test
    void createOrderFromCart_UserNotFound() {

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> orderService.createOrderFromCart(
                        1L,
                        "Street",
                        "City",
                        "State",
                        "123",
                        "USA",
                        null
                )
        );
    }

    @Test
    void createOrderFromCart_EmptyCart() {

        Cart emptyCart = Cart.builder()
                .id(1L)
                .user(user)
                .items(new ArrayList<>())
                .build();

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(cartService.getOrCreateCart(1L))
                .thenReturn(emptyCart);

        assertThrows(
                AuthException.class,
                () -> orderService.createOrderFromCart(
                        1L,
                        "Street",
                        "City",
                        "State",
                        "123",
                        "USA",
                        null
                )
        );
    }

    @Test
    void confirmOrder_Success() {

        order.setOrderStatus(OrderStatus.PENDING);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result = orderService.confirmOrder(1L);

        assertEquals(OrderStatus.PROCESSING, result.getOrderStatus());
        assertEquals(PaymentStatus.SUCCESS, result.getPaymentStatus());

        verify(inventoryService)
                .deductInventory(product.getId(), 2);

        verify(notificationService)
                .sendOrderConfirmedNotification(any(Order.class));
    }

    @Test
    void confirmOrder_InvalidTransition() {

        order.setOrderStatus(OrderStatus.DELIVERED);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        assertThrows(
                AuthException.class,
                () -> orderService.confirmOrder(1L)
        );
    }

    @Test
    void shipOrder_Success() {

        order.setOrderStatus(OrderStatus.PROCESSING);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result =
                orderService.shipOrder(1L, "TRACK123");

        assertEquals(OrderStatus.SHIPPED, result.getOrderStatus());
        assertEquals("TRACK123", result.getTrackingNumber());

        verify(notificationService)
                .sendOrderShippedNotification(any(Order.class));
    }

    @Test
    void deliverOrder_Success() {

        order.setOrderStatus(OrderStatus.SHIPPED);

        when(orderRepository.findById(1L))
                .thenReturn(Optional.of(order));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result =
                orderService.deliverOrder(1L);

        assertEquals(OrderStatus.DELIVERED, result.getOrderStatus());

        verify(notificationService)
                .sendOrderDeliveredNotification(any(Order.class));
    }

    @Test
    void cancelOrder_AsOwner_Success() {

        order.setOrderStatus(OrderStatus.PENDING);

        when(orderRepository.findByIdWithItems(1L))
                .thenReturn(Optional.of(order));

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Order result =
                orderService.cancelOrder(1L, 1L, false);

        assertEquals(OrderStatus.CANCELLED, result.getOrderStatus());

        verify(inventoryService)
                .releaseReservedInventory(product.getId(), 2);

        verify(notificationService)
                .sendOrderCancelledNotification(any(Order.class));
    }

    @Test
    void cancelOrder_Forbidden() {

        when(orderRepository.findByIdWithItems(1L))
                .thenReturn(Optional.of(order));

        assertThrows(
                AuthException.class,
                () -> orderService.cancelOrder(
                        1L,
                        999L,
                        false
                )
        );
    }

    @Test
    void getOrderById_AsOwner() {

        when(orderRepository.findByIdWithItems(1L))
                .thenReturn(Optional.of(order));

        Order result =
                orderService.getOrderById(
                        1L,
                        1L,
                        false
                );

        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getOrderById_Forbidden() {

        when(orderRepository.findByIdWithItems(1L))
                .thenReturn(Optional.of(order));

        assertThrows(
                AuthException.class,
                () -> orderService.getOrderById(
                        1L,
                        999L,
                        false
                )
        );
    }

    @Test
    void getOrdersForUser_Success() {

        when(orderRepository.findByUserIdWithItems(1L))
                .thenReturn(List.of(order));

        List<Order> result =
                orderService.getOrdersForUser(1L);

        assertEquals(1, result.size());
    }

    @Test
    void getOrdersByStatus_Success() {

        when(orderRepository.findByOrderStatus(OrderStatus.PENDING))
                .thenReturn(List.of(order));

        List<Order> result =
                orderService.getOrdersByStatus(OrderStatus.PENDING);

        assertEquals(1, result.size());
    }
}