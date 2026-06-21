package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.Notification;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.NotificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "User notification management")
@SecurityRequirement(name = "Bearer Authentication")
public class NotificationController {

    private final NotificationService notificationService;

    // ─────────────────────────────────────────────────────────────
    // GET ALL NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get all notifications for the authenticated user")
    public ResponseEntity<List<Notification>> getNotifications(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return ResponseEntity.ok(notificationService.getNotificationsForUser(userId));
    }

    // ─────────────────────────────────────────────────────────────
    // GET UNREAD NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/unread")
    @Operation(summary = "Get unread notifications for the authenticated user")
    public ResponseEntity<List<Notification>> getUnreadNotifications(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return ResponseEntity.ok(notificationService.getUnreadNotificationsForUser(userId));
    }

    // ─────────────────────────────────────────────────────────────
    // GET UNREAD COUNT
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/unread/count")
    @Operation(summary = "Get the count of unread notifications")
    public ResponseEntity<Map<String, Long>> getUnreadCount(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        long count = notificationService.getUnreadCount(userId);
        return ResponseEntity.ok(Map.of("unreadCount", count));
    }

    // ─────────────────────────────────────────────────────────────
    // MARK AS READ (single)
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/read")
    @Operation(summary = "Mark a single notification as read")
    public ResponseEntity<Map<String, String>> markAsRead(
            @PathVariable Long id,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        notificationService.markAsRead(id, userId);
        log.info("Notification {} marked as read for userId={}", id, userId);
        return ResponseEntity.ok(Map.of("message", "Notification marked as read"));
    }

    // ─────────────────────────────────────────────────────────────
    // MARK ALL AS READ
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/read-all")
    @Operation(summary = "Mark all notifications as read")
    public ResponseEntity<Map<String, Object>> markAllAsRead(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        int updated = notificationService.markAllAsRead(userId);
        log.info("Marked {} notifications as read for userId={}", updated, userId);
        return ResponseEntity.ok(Map.of("message", "All notifications marked as read", "updatedCount", updated));
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required", "UNAUTHORIZED");
        }
        return ((PayFlowUserDetails) authentication.getPrincipal()).getId();
    }
}