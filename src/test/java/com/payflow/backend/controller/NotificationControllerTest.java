package com.payflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.config.SecurityConfig;
import com.payflow.backend.config.TestWebMvcSecurityConfig;
import com.payflow.backend.domain.entity.Notification;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.NotificationType;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.security.JwtAuthenticationFilter;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.NotificationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = NotificationController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestWebMvcSecurityConfig.class)
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private NotificationService notificationService;

    private Notification notification;
    private UsernamePasswordAuthenticationToken userToken;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash("hashed")
                .firstName("John")
                .lastName("Doe")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
        PayFlowUserDetails userDetails = new PayFlowUserDetails(user);
        userToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        notification = Notification.builder()
                .id(1L)
                .user(user)
                .type(NotificationType.ORDER_CREATED)
                .title("Order Created")
                .message("Your order ORD-2026-000001 has been placed.")
                .relatedEntityType("ORDER")
                .relatedEntityId(1L)
                .isRead(false)
                .createdAt(LocalDateTime.now())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // GET ALL NOTIFICATIONS
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnAllNotificationsForUser() throws Exception {
        when(notificationService.getNotificationsForUser(1L)).thenReturn(List.of(notification));

        mockMvc.perform(get("/api/notifications")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(1))
                .andExpect(jsonPath("$[0].title").value("Order Created"))
                .andExpect(jsonPath("$[0].isRead").value(false));

        verify(notificationService).getNotificationsForUser(1L);
    }

    @Test
    void shouldReturnEmptyListWhenNoNotifications() throws Exception {
        when(notificationService.getNotificationsForUser(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─────────────────────────────────────────────────────────────
    // GET UNREAD NOTIFICATIONS
    // ────────��────────────────────────────────────────────────────

    @Test
    void shouldReturnUnreadNotifications() throws Exception {
        when(notificationService.getUnreadNotificationsForUser(1L)).thenReturn(List.of(notification));

        mockMvc.perform(get("/api/notifications/unread")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isRead").value(false));

        verify(notificationService).getUnreadNotificationsForUser(1L);
    }

    @Test
    void shouldReturnEmptyUnreadList() throws Exception {
        when(notificationService.getUnreadNotificationsForUser(1L)).thenReturn(List.of());

        mockMvc.perform(get("/api/notifications/unread")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    // ─────────────────────────────────────────────────────────────
    // GET UNREAD COUNT
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnUnreadCount() throws Exception {
        when(notificationService.getUnreadCount(1L)).thenReturn(3L);

        mockMvc.perform(get("/api/notifications/unread/count")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(3));

        verify(notificationService).getUnreadCount(1L);
    }

    @Test
    void shouldReturnZeroWhenNoUnread() throws Exception {
        when(notificationService.getUnreadCount(1L)).thenReturn(0L);

        mockMvc.perform(get("/api/notifications/unread/count")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    // ─────────────���───────────────────────────────────────────────
    // MARK AS READ (single)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldMarkNotificationAsRead() throws Exception {
        doNothing().when(notificationService).markAsRead(1L, 1L);

        mockMvc.perform(post("/api/notifications/1/read")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Notification marked as read"));

        verify(notificationService).markAsRead(1L, 1L);
    }

    @Test
    void shouldReturn404WhenNotificationNotFound() throws Exception {
        doThrow(new UserNotFoundException("Notification not found: 99"))
                .when(notificationService).markAsRead(99L, 1L);

        mockMvc.perform(post("/api/notifications/99/read")
                        .with(authentication(userToken)))
                .andExpect(status().isNotFound());

        verify(notificationService).markAsRead(99L, 1L);
    }

    // ─────────────────────────────────────────────────────────────
    // MARK ALL AS READ
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldMarkAllNotificationsAsRead() throws Exception {
        when(notificationService.markAllAsRead(1L)).thenReturn(5);

        mockMvc.perform(post("/api/notifications/read-all")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("All notifications marked as read"))
                .andExpect(jsonPath("$.updatedCount").value(5));

        verify(notificationService).markAllAsRead(1L);
    }

    @Test
    void shouldReturnZeroUpdatedWhenAllAlreadyRead() throws Exception {
        when(notificationService.markAllAsRead(1L)).thenReturn(0);

        mockMvc.perform(post("/api/notifications/read-all")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updatedCount").value(0));
    }
}