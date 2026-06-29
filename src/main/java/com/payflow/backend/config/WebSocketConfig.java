package com.payflow.backend.config;

import com.payflow.backend.websocket.OrderWebSocketHandler;
import com.payflow.backend.websocket.WebSocketInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Slf4j
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    @Autowired
    private OrderWebSocketHandler orderWebSocketHandler;

    @Autowired
    private WebSocketInterceptor webSocketInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        log.info("[WebSocket] Registering WebSocket handlers");

        registry
                .addHandler(orderWebSocketHandler, "/ws/orders")
                .addInterceptors(webSocketInterceptor)
                .setAllowedOrigins("*");

        log.info("[WebSocket] WebSocket endpoint registered: /ws/orders");
    }
}
