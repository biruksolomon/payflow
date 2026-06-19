package com.payflow.backend.repository;

import com.payflow.backend.domain.entity.Notification;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.NotificationType;
import com.payflow.backend.domain.enums.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import jakarta.persistence.EntityManager;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    private User user;

    @BeforeEach
    void setup() {

        user = User.builder()
                .email("user@test.com")
                .passwordHash("password")
                .firstName("John")
                .lastName("Doe")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();

        entityManager.persist(user);

        Notification unread1 = Notification.builder()
                .user(user)
                .type(NotificationType.PAYMENT_SUCCESS)
                .title("Payment 1")
                .message("Message 1")
                .isRead(false)
                .build();

        Notification unread2 = Notification.builder()
                .user(user)
                .type(NotificationType.ORDER_CREATED)
                .title("Order")
                .message("Message 2")
                .isRead(false)
                .build();

        Notification read = Notification.builder()
                .user(user)
                .type(NotificationType.PAYMENT_SUCCESS)
                .title("Read")
                .message("Message 3")
                .isRead(true)
                .build();

        entityManager.persist(unread1);
        entityManager.persist(unread2);
        entityManager.persist(read);

        entityManager.flush();
    }

    @Test
    void shouldFindByUserIdOrderByCreatedAtDesc() {

        List<Notification> notifications =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(user.getId());

        assertEquals(3, notifications.size());
    }

    @Test
    void shouldFindUnreadNotifications() {

        List<Notification> notifications =
                notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId());

        assertEquals(2, notifications.size());

        assertTrue(
                notifications.stream()
                        .allMatch(n -> !n.getIsRead())
        );
    }

    @Test
    void shouldCountUnreadNotifications() {

        long count =
                notificationRepository.countByUserIdAndIsReadFalse(user.getId());

        assertEquals(2, count);
    }

    @Test
    void shouldFindByType() {

        List<Notification> notifications =
                notificationRepository.findByUserIdAndTypeOrderByCreatedAtDesc(
                        user.getId(),
                        NotificationType.PAYMENT_SUCCESS
                );

        assertEquals(2, notifications.size());

        assertTrue(
                notifications.stream()
                        .allMatch(n ->
                                n.getType() == NotificationType.PAYMENT_SUCCESS)
        );
    }

    @Test
    void shouldMarkAllAsReadForUser() {

        int updated =
                notificationRepository.markAllAsReadForUser(user.getId());

        entityManager.flush();
        entityManager.clear();

        assertEquals(2, updated);

        List<Notification> unread =
                notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(user.getId());

        assertEquals(0, unread.size());
    }

    @Test
    void shouldReturnEmptyWhenUserHasNoNotifications() {

        List<Notification> notifications =
                notificationRepository.findByUserIdOrderByCreatedAtDesc(999L);

        assertTrue(notifications.isEmpty());
    }

    @Test
    void shouldReturnZeroUnreadCountWhenNoNotificationsExist() {

        long count =
                notificationRepository.countByUserIdAndIsReadFalse(999L);

        assertEquals(0, count);
    }
}