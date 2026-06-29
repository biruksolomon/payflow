package com.payflow.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.websocket.dto.OrderSuccessMessage;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
public class OrderWebSocketHandler extends TextWebSocketHandler {

    // Map<userId, Set<WebSocketSessions>>
    private final Map<Long, Set<WebSocketSession>> userSessions = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            Long userIdLong = Long.parseLong(userId);
            userSessions.computeIfAbsent(userIdLong, k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                    .add(session);
            log.info("[WebSocket] User {} connected. Session ID: {}", userIdLong, session.getId());
        } else {
            session.close();
            log.warn("[WebSocket] Connection rejected: No userId in session attributes");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session,@NonNull CloseStatus status) throws Exception {
        String userId = (String) session.getAttributes().get("userId");
        if (userId != null) {
            Long userIdLong = Long.parseLong(userId);
            Set<WebSocketSession> sessions = userSessions.get(userIdLong);
            if (sessions != null) {
                sessions.remove(session);
                if (sessions.isEmpty()) {
                    userSessions.remove(userIdLong);
                }
            }
            log.info("[WebSocket] User {} disconnected. Session ID: {}", userIdLong, session.getId());
        }
    }

    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) throws Exception {
        log.debug("[WebSocket] Received message: {}", message.getPayload());
    }

    /**
     * Broadcast order success notification to a specific user across all their WebSocket connections
     */
    public void broadcastOrderSuccess(Long userId, OrderSuccessMessage message) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        if (sessions != null && !sessions.isEmpty()) {
            try {
                String jsonMessage = objectMapper.writeValueAsString(message);
                TextMessage textMessage = new TextMessage(jsonMessage);

                for (WebSocketSession session : sessions) {
                    if (session.isOpen()) {
                        try {
                            session.sendMessage(textMessage);
                            log.debug("[WebSocket] Sent order success message to user {} (session: {})", userId, session.getId());
                        } catch (IOException e) {
                            log.error("[WebSocket] Error sending message to session {}: {}", session.getId(), e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                log.error("[WebSocket] Error broadcasting order success to user {}: {}", userId, e.getMessage(), e);
            }
        } else {
            log.warn("[WebSocket] No active sessions for user {}. Message queued for delivery.", userId);
        }
    }

    /**
     * Get count of active connections for a user (useful for monitoring)
     */
    public int getActiveSessionCount(Long userId) {
        Set<WebSocketSession> sessions = userSessions.get(userId);
        return sessions != null ? sessions.size() : 0;
    }
}
