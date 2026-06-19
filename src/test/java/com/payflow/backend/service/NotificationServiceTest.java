package com.payflow.backend.service;

import com.payflow.backend.domain.entity.Notification;
import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.NotificationType;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.NotificationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User user;
    private Order order;
    private Payment payment;

    @BeforeEach
    void setup() {

        user = User.builder()
                .id(1L)
                .email("user@test.com")
                .build();

        order = Order.builder()
                .id(10L)
                .orderNumber("ORD-100")
                .trackingNumber("TRACK123")
                .user(user)
                .build();

        payment = Payment.builder()
                .id(20L)
                .user(user)
                .amount(BigDecimal.valueOf(100))
                .refundedAmount(BigDecimal.valueOf(50))
                .currency(Currency.USD)
                .order(order)
                .build();
    }

    @Test
    void shouldGetNotificationsForUser() {

        List<Notification> notifications = List.of(new Notification());

        when(notificationRepository.findByUserIdOrderByCreatedAtDesc(1L))
                .thenReturn(notifications);

        List<Notification> result =
                notificationService.getNotificationsForUser(1L);

        assertEquals(1, result.size());

        verify(notificationRepository)
                .findByUserIdOrderByCreatedAtDesc(1L);
    }

    @Test
    void shouldGetUnreadNotificationsForUser() {

        List<Notification> notifications = List.of(new Notification());

        when(notificationRepository
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(1L))
                .thenReturn(notifications);

        List<Notification> result =
                notificationService.getUnreadNotificationsForUser(1L);

        assertEquals(1, result.size());

        verify(notificationRepository)
                .findByUserIdAndIsReadFalseOrderByCreatedAtDesc(1L);
    }

    @Test
    void shouldGetUnreadCount() {

        when(notificationRepository.countByUserIdAndIsReadFalse(1L))
                .thenReturn(5L);

        long result = notificationService.getUnreadCount(1L);

        assertEquals(5L, result);

        verify(notificationRepository)
                .countByUserIdAndIsReadFalse(1L);
    }

    @Test
    void shouldMarkNotificationAsRead() {

        Notification notification = Notification.builder()
                .id(1L)
                .user(user)
                .isRead(false)
                .build();

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        notificationService.markAsRead(1L, 1L);

        assertTrue(notification.getIsRead());
        assertNotNull(notification.getReadAt());

        verify(notificationRepository).save(notification);
    }

    @Test
    void shouldThrowWhenNotificationNotFound() {

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> notificationService.markAsRead(1L, 1L)
        );

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenUserNotOwner() {

        User otherUser = User.builder()
                .id(999L)
                .build();

        Notification notification = Notification.builder()
                .id(1L)
                .user(otherUser)
                .build();

        when(notificationRepository.findById(1L))
                .thenReturn(Optional.of(notification));

        assertThrows(
                SecurityException.class,
                () -> notificationService.markAsRead(1L, 1L)
        );

        verify(notificationRepository, never()).save(any());
    }

    @Test
    void shouldMarkAllAsRead() {

        when(notificationRepository.markAllAsReadForUser(1L))
                .thenReturn(7);

        int result = notificationService.markAllAsRead(1L);

        assertEquals(7, result);

        verify(notificationRepository)
                .markAllAsReadForUser(1L);
    }

    @Test
    void shouldSendOrderCreatedNotification() {

        notificationService.sendOrderCreatedNotification(order);

        verifyNotificationSaved(
                NotificationType.ORDER_CREATED,
                "ORDER",
                10L
        );
    }

    @Test
    void shouldSendOrderConfirmedNotification() {

        notificationService.sendOrderConfirmedNotification(order);

        verifyNotificationSaved(
                NotificationType.ORDER_CONFIRMED,
                "ORDER",
                10L
        );
    }

    @Test
    void shouldSendOrderShippedNotification() {

        notificationService.sendOrderShippedNotification(order);

        verifyNotificationSaved(
                NotificationType.ORDER_SHIPPED,
                "ORDER",
                10L
        );
    }

    @Test
    void shouldSendOrderDeliveredNotification() {

        notificationService.sendOrderDeliveredNotification(order);

        verifyNotificationSaved(
                NotificationType.ORDER_DELIVERED,
                "ORDER",
                10L
        );
    }

    @Test
    void shouldSendOrderCancelledNotification() {

        notificationService.sendOrderCancelledNotification(order);

        verify(notificationRepository)
                .save(any(Notification.class));
    }

    @Test
    void shouldSendPaymentSuccessNotification() {

        notificationService.sendPaymentSuccessNotification(payment);

        verifyNotificationSaved(
                NotificationType.PAYMENT_SUCCESS,
                "PAYMENT",
                20L
        );
    }

    @Test
    void shouldSendPaymentFailedNotification() {

        notificationService.sendPaymentFailedNotification(payment);

        verifyNotificationSaved(
                NotificationType.PAYMENT_FAILED,
                "PAYMENT",
                20L
        );
    }

    @Test
    void shouldSendRefundInitiatedNotification() {

        notificationService.sendRefundInitiatedNotification(payment);

        verifyNotificationSaved(
                NotificationType.REFUND_INITIATED,
                "PAYMENT",
                20L
        );
    }

    @Test
    void shouldSendRefundCompletedNotification() {

        notificationService.sendRefundCompletedNotification(payment);

        verifyNotificationSaved(
                NotificationType.REFUND_COMPLETED,
                "PAYMENT",
                20L
        );
    }

    @Test
    void shouldSwallowExceptionWhenNotificationSaveFails() {

        doThrow(new RuntimeException("DB Error"))
                .when(notificationRepository)
                .save(any(Notification.class));

        assertDoesNotThrow(
                () -> notificationService.sendOrderCreatedNotification(order)
        );
    }

    private void verifyNotificationSaved(
            NotificationType type,
            String entityType,
            Long entityId
    ) {

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository)
                .save(captor.capture());

        Notification notification = captor.getValue();

        assertEquals(type, notification.getType());
        assertEquals(entityType, notification.getRelatedEntityType());
        assertEquals(entityId, notification.getRelatedEntityId());
        assertEquals(user, notification.getUser());
    }
}