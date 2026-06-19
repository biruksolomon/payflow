package com.payflow.backend.service;

import com.payflow.backend.domain.entity.Notification;
import com.payflow.backend.domain.entity.Order;
import com.payflow.backend.domain.entity.Payment;
import com.payflow.backend.domain.enums.NotificationType;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationService {

    private final NotificationRepository notificationRepository;

    // ─────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Notification> getNotificationsForUser(Long userId) {
        return notificationRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public List<Notification> getUnreadNotificationsForUser(Long userId) {
        return notificationRepository.findByUserIdAndIsReadFalseOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public long getUnreadCount(Long userId) {
        return notificationRepository.countByUserIdAndIsReadFalse(userId);
    }

    // ─────────────────────────────────────────────────────────────
    // MUTATIONS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void markAsRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new UserNotFoundException("Notification not found: " + notificationId));

        if (!notification.getUser().getId().equals(userId)) {
            throw new SecurityException("Not authorized to update this notification");
        }

        notification.markAsRead();
        notificationRepository.save(notification);
        log.info("Notification {} marked as read for userId={}", notificationId, userId);
    }

    @Transactional
    public int markAllAsRead(Long userId) {
        int updated = notificationRepository.markAllAsReadForUser(userId);
        log.info("Marked {} notifications as read for userId={}", updated, userId);
        return updated;
    }

    // ─────────────────────────────────────────────────────────────
    // ORDER EVENTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void sendOrderCreatedNotification(Order order) {
        save(Notification.builder()
                .user(order.getUser())
                .type(NotificationType.ORDER_CREATED)
                .title(NotificationType.ORDER_CREATED.getDisplayName())
                .message("Your order " + order.getOrderNumber() + " has been placed successfully.")
                .relatedEntityType("ORDER")
                .relatedEntityId(order.getId())
                .build());
    }

    @Transactional
    public void sendOrderConfirmedNotification(Order order) {
        save(Notification.builder()
                .user(order.getUser())
                .type(NotificationType.ORDER_CONFIRMED)
                .title(NotificationType.ORDER_CONFIRMED.getDisplayName())
                .message("Your order " + order.getOrderNumber() + " has been confirmed and is being prepared.")
                .relatedEntityType("ORDER")
                .relatedEntityId(order.getId())
                .build());
    }

    @Transactional
    public void sendOrderShippedNotification(Order order) {
        save(Notification.builder()
                .user(order.getUser())
                .type(NotificationType.ORDER_SHIPPED)
                .title(NotificationType.ORDER_SHIPPED.getDisplayName())
                .message("Your order " + order.getOrderNumber() + " has been shipped."
                        + (order.getTrackingNumber() != null
                        ? " Tracking: " + order.getTrackingNumber()
                        : ""))
                .relatedEntityType("ORDER")
                .relatedEntityId(order.getId())
                .build());
    }

    @Transactional
    public void sendOrderDeliveredNotification(Order order) {
        save(Notification.builder()
                .user(order.getUser())
                .type(NotificationType.ORDER_DELIVERED)
                .title(NotificationType.ORDER_DELIVERED.getDisplayName())
                .message("Your order " + order.getOrderNumber() + " has been delivered.")
                .relatedEntityType("ORDER")
                .relatedEntityId(order.getId())
                .build());
    }

    @Transactional
    public void sendOrderCancelledNotification(Order order) {
        // Re-use ORDER_CONFIRMED slot; no CANCELLED enum value, use a generic title
        save(Notification.builder()
                .user(order.getUser())
                .type(NotificationType.ORDER_CONFIRMED)
                .title("Order Cancelled")
                .message("Your order " + order.getOrderNumber() + " has been cancelled.")
                .relatedEntityType("ORDER")
                .relatedEntityId(order.getId())
                .build());
    }

    // ─────────────────────────────────────────────────────────────
    // PAYMENT EVENTS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void sendPaymentSuccessNotification(Payment payment) {
        save(Notification.builder()
                .user(payment.getUser())
                .type(NotificationType.PAYMENT_SUCCESS)
                .title(NotificationType.PAYMENT_SUCCESS.getDisplayName())
                .message("Payment of " + payment.getAmount() + " " + payment.getCurrency()
                        + " for order #" + payment.getOrder().getOrderNumber() + " was successful.")
                .relatedEntityType("PAYMENT")
                .relatedEntityId(payment.getId())
                .build());
    }

    @Transactional
    public void sendPaymentFailedNotification(Payment payment) {
        save(Notification.builder()
                .user(payment.getUser())
                .type(NotificationType.PAYMENT_FAILED)
                .title(NotificationType.PAYMENT_FAILED.getDisplayName())
                .message("Payment for order #" + payment.getOrder().getOrderNumber()
                        + " failed. Please try again or use a different payment method.")
                .relatedEntityType("PAYMENT")
                .relatedEntityId(payment.getId())
                .build());
    }

    @Transactional
    public void sendRefundInitiatedNotification(Payment payment) {
        save(Notification.builder()
                .user(payment.getUser())
                .type(NotificationType.REFUND_INITIATED)
                .title(NotificationType.REFUND_INITIATED.getDisplayName())
                .message("A refund of " + payment.getRefundedAmount() + " " + payment.getCurrency()
                        + " has been initiated for order #" + payment.getOrder().getOrderNumber() + ".")
                .relatedEntityType("PAYMENT")
                .relatedEntityId(payment.getId())
                .build());
    }

    @Transactional
    public void sendRefundCompletedNotification(Payment payment) {
        save(Notification.builder()
                .user(payment.getUser())
                .type(NotificationType.REFUND_COMPLETED)
                .title(NotificationType.REFUND_COMPLETED.getDisplayName())
                .message("Your refund of " + payment.getRefundedAmount() + " " + payment.getCurrency()
                        + " has been completed for order #" + payment.getOrder().getOrderNumber() + ".")
                .relatedEntityType("PAYMENT")
                .relatedEntityId(payment.getId())
                .build());
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE
    // ─────────────────────────────────────────────────────────────

    private void save(Notification notification) {
        try {
            notificationRepository.save(notification);
            log.info("Notification saved: type={}, userId={}, relatedId={}",
                    notification.getType(), notification.getUser().getId(), notification.getRelatedEntityId());
        } catch (Exception e) {
            // Notifications are non-critical — log but do not propagate
            log.error("Failed to save notification type={}: {}", notification.getType(), e.getMessage());
        }
    }
}