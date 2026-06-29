package com.payflow.backend.websocket;


import com.payflow.backend.security.JwtTokenProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.util.Map;

@Slf4j
@Component
public class WebSocketInterceptor implements HandshakeInterceptor {

    @Autowired
    private JwtTokenProvider jwtProvider;

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) throws Exception {
        try {
            // Extract JWT token from query parameter or header
            String token = extractToken(request);

            if (token == null) {
                log.warn("[WebSocket] Connection rejected: No JWT token provided");
                return false;
            }

            // Validate token and extract userId
            Long userId = jwtProvider.getUserIdFromToken(token);

            if (userId == null) {
                log.warn("[WebSocket] Connection rejected: Invalid JWT token");
                return false;
            }

            // Store userId in session attributes for later use
            attributes.put("userId", String.valueOf(userId));
            log.info("[WebSocket] Handshake successful for user {}", userId);
            return true;

        } catch (Exception e) {
            log.error("[WebSocket] Handshake failed: {}", e.getMessage());
            return false;
        }
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        if (exception != null) {
            log.error("[WebSocket] Error after handshake: {}", exception.getMessage());
        }
    }

    /**
     * Extract JWT token from query parameter or Authorization header
     */
    private String extractToken(ServerHttpRequest request) {
        // Try query parameter first
        String query = request.getURI().getQuery();
        if (query != null && query.contains("token=")) {
            String[] params = query.split("&");
            for (String param : params) {
                if (param.startsWith("token=")) {
                    return param.substring(6);
                }
            }
        }

        // Try Authorization header
        String authHeader = request.getHeaders().getFirst("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }

        return null;
    }
}
