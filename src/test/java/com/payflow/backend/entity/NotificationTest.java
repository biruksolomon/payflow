package com.payflow.backend.entity;

import com.payflow.backend.domain.entity.Notification;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.NotificationType;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

class NotificationTest {

    @Test
    void shouldBuildNotificationSuccessfully() {

        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash("password")
                .firstName("John")
                .lastName("Doe")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        Notification notification = Notification.builder()
                .id(1L)
                .user(user)
                .type(NotificationType.PAYMENT_SUCCESS)
                .title("Payment Received")
                .message("You received a payment")
                .relatedEntityType("PAYMENT")
                .relatedEntityId(100L)
                .build();

        assertEquals(1L, notification.getId());
        assertEquals(user, notification.getUser());
        assertEquals(NotificationType.PAYMENT_SUCCESS, notification.getType());
        assertEquals("Payment Received", notification.getTitle());
        assertEquals("You received a payment", notification.getMessage());
        assertEquals("PAYMENT", notification.getRelatedEntityType());
        assertEquals(100L, notification.getRelatedEntityId());

        // Builder.Default
        assertFalse(notification.getIsRead());
    }

    @Test
    void shouldMarkNotificationAsRead() {

        Notification notification = Notification.builder()
                .title("Test")
                .message("Message")
                .type(NotificationType.PAYMENT_SUCCESS)
                .build();

        assertFalse(notification.getIsRead());
        assertNull(notification.getReadAt());

        notification.markAsRead();

        assertTrue(notification.getIsRead());
        assertNotNull(notification.getReadAt());
    }

    @Test
    void shouldSupportNoArgsConstructor() {

        Notification notification = new Notification();

        assertNotNull(notification);
    }

    @Test
    void shouldSupportAllArgsConstructor() {

        User user = new User();

        LocalDateTime now = LocalDateTime.now();

        Notification notification = new Notification(
                1L,
                user,
                NotificationType.PAYMENT_SUCCESS,
                "Title",
                "Message",
                "PAYMENT",
                5L,
                true,
                now,
                now
        );

        assertEquals(1L, notification.getId());
        assertEquals(user, notification.getUser());
        assertEquals(NotificationType.PAYMENT_SUCCESS, notification.getType());
        assertTrue(notification.getIsRead());
        assertEquals(now, notification.getReadAt());
        assertEquals(now, notification.getCreatedAt());
    }

    @Test
    void shouldTestEqualsAndHashCode() {

        Notification n1 = Notification.builder()
                .id(1L)
                .build();

        Notification n2 = Notification.builder()
                .id(1L)
                .build();

        assertEquals(n1, n2);
        assertEquals(n1.hashCode(), n2.hashCode());
    }

    @Test
    void shouldTestToString() {

        Notification notification = Notification.builder()
                .id(1L)
                .title("Test")
                .message("Message")
                .build();

        String result = notification.toString();

        assertNotNull(result);
        assertTrue(result.contains("Test"));
    }
}