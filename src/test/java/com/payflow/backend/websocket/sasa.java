/*
    package com.payflow.backend.websocket;
    
    import com.fasterxml.jackson.databind.ObjectMapper;
    import com.payflow.backend.service.NotificationService;
    import com.payflow.backend.service.OrderService;
    import com.payflow.backend.service.PaymentService;
    import com.payflow.backend.websocket.dto.OrderSuccessMessage;
    import io.jsonwebtoken.Jwts;
    import io.jsonwebtoken.SignatureAlgorithm;
    import lombok.extern.slf4j.Slf4j;
    import org.junit.jupiter.api.BeforeEach;
    import org.junit.jupiter.api.DisplayName;
    import org.junit.jupiter.api.Test;
    import org.junit.jupiter.api.extension.ExtendWith;
    import org.mockito.ArgumentCaptor;
    import org.mockito.Mock;
    import org.mockito.junit.jupiter.MockitoExtension;
    import org.springframework.web.socket.TextMessage;
    import org.springframework.web.socket.WebSocketSession;
    
    import java.time.LocalDateTime;
    import java.util.Date;
    
    import static org.junit.jupiter.api.Assertions.*;
    import static org.mockito.Mockito.*;
    
    @Slf4j
    @ExtendWith(MockitoExtension.class)
    @DisplayName("WebSocket Integration Tests")
    class WebSocketIntegrationTest {
    
        private OrderWebSocketHandler webSocketHandler;
        private ObjectMapper objectMapper;
    
        @Mock
        private WebSocketSession mockSession1;
    
        @Mock
        private WebSocketSession mockSession2;
    
        @Mock
        private OrderService mockOrderService;
    
        @Mock
        private NotificationService mockNotificationService;
    
        @BeforeEach
        void setUp() {
            webSocketHandler = new OrderWebSocketHandler();
            // paymentRepository - not used in this test
            // orderRepository - not used in this test
            // userRepository - not used in this test
            PaymentService paymentService = new PaymentService(
                    null, // paymentRepository - not used in this test
                    null, // orderRepository - not used in this test
                    null, // userRepository - not used in this test
                    mockOrderService,
                    mockNotificationService,
                    webSocketHandler
            );
            objectMapper = new ObjectMapper();
            objectMapper.findAndRegisterModules();
        }
    
        private String generateValidToken(Long userId) {
            String jwtSecret = "test-secret-key-this-is-a-very-long-secret-for-testing-purposes-only";
            return Jwts.builder().subject(userId.toString()).issuedAt(new Date()).expiration(new Date(System.currentTimeMillis() + 3600000)).signWith(SignatureAlgorithm.HS512, jwtSecret)
                    .compact();
        }
    
        @Test
        @DisplayName("Test 1: End-to-End - Payment Success broadcasts to user's WebSocket sessions")
        void testPaymentSuccessBroadcastsToWebSocket() throws Exception {
            // Setup
            Long userId = 1L;
            Long orderId = 100L;
            mockSession1.getAttributes().put("userId", userId);
            when(mockSession1.getId()).thenReturn("session-1");
    
            // Connect user
            webSocketHandler.afterConnectionEstablished(mockSession1);
            assertEquals(1, webSocketHandler.getActiveSessionCount(userId));
    
            // Create order success message
            OrderSuccessMessage message = OrderSuccessMessage.builder()
                    .type("ORDER_SUCCESS")
                    .orderId(orderId)
                    .orderNumber("ORD-001")
                    .status("CONFIRMED")
                    .timestamp(LocalDateTime.now())
                    .message("Your order has been successfully placed and is being processed!")
                    .build();
    
            // Broadcast
            webSocketHandler.broadcastOrderSuccess(userId, message);
    
            // Verify message was sent to session
            ArgumentCaptor<TextMessage> messageCaptor = ArgumentCaptor.forClass(TextMessage.class);
            verify(mockSession1).sendMessage(messageCaptor.capture());
    
            String sentJson = messageCaptor.getValue().getPayload();
            OrderSuccessMessage receivedMessage = objectMapper.readValue(sentJson, OrderSuccessMessage.class);
    
            assertEquals("ORDER_SUCCESS", receivedMessage.getType());
            assertEquals(orderId, receivedMessage.getOrderId());
            assertEquals("ORD-001", receivedMessage.getOrderNumber());
            assertEquals(99.99, receivedMessage.getAmount());
            log.info("[Test 1] ✓ Payment success broadcasted to user's WebSocket session");
        }
    
        @Test
        @DisplayName("Test 2: Multi-user isolation - Each user only receives their messages")
        void testMultiUserIsolation() throws Exception {
            // Setup
            Long userId1 = 1L;
            Long userId2 = 2L;
            Long orderId1 = 100L;
            Long orderId2 = 200L;
    
            mockSession1.getAttributes().put("userId", userId1);
            mockSession2.getAttributes().put("userId", userId2);
            when(mockSession1.getId()).thenReturn("session-1");
            when(mockSession2.getId()).thenReturn("session-2");
    
            // Connect both users
            webSocketHandler.afterConnectionEstablished(mockSession1);
            webSocketHandler.afterConnectionEstablished(mockSession2);
    
            // Create messages
            OrderSuccessMessage messageUser1 = OrderSuccessMessage.builder()
                    .type("ORDER_SUCCESS")
                    .orderId(orderId1)
                    .orderNumber("ORD-001")
                    .status("CONFIRMED")
                    .timestamp(LocalDateTime.now())
                    .message("Your order confirmed!")
                    .build();
    
            OrderSuccessMessage messageUser2 = OrderSuccessMessage.builder()
                    .type("ORDER_SUCCESS")
                    .orderId(orderId2)
                    .orderNumber("ORD-002")
                    .status("CONFIRMED")
                    .timestamp(LocalDateTime.now())
                    .message("Your order confirmed!")
                    .build();
    
            // Broadcast to user 1 only
            webSocketHandler.broadcastOrderSuccess(userId1, messageUser1);
    
            // Verify user 1 received message
            verify(mockSession1).sendMessage(any(TextMessage.class));
            // Verify user 2 did NOT receive user 1's message
            verify(mockSession2, never()).sendMessage(any(TextMessage.class));
    
            // Reset mocks
            reset(mockSession1, mockSession2);
    
            // Broadcast to user 2 only
            webSocketHandler.broadcastOrderSuccess(userId2, messageUser2);
    
            // Verify user 2 received message
            verify(mockSession2).sendMessage(any(TextMessage.class));
            // Verify user 1 did NOT receive user 2's message
            verify(mockSession1, never()).sendMessage(any(TextMessage.class));
    
            log.info("[Test 2] ✓ User isolation verified - messages received only by intended user");
        }
    
        @Test
        @DisplayName("Test 3: Multi-tab scenario - All user's tabs receive same message")
        void testMultiTabBroadcast() throws Exception {
            // Setup
            Long userId = 1L;
            Long orderId = 100L;
    
            mockSession1.getAttributes().put("userId", userId);
            mockSession2.getAttributes().put("userId", userId);
            when(mockSession1.getId()).thenReturn("session-1");
            when(mockSession2.getId()).thenReturn("session-2");
    
            // User connects from multiple tabs/devices
            webSocketHandler.afterConnectionEstablished(mockSession1);
            webSocketHandler.afterConnectionEstablished(mockSession2);
            assertEquals(2, webSocketHandler.getActiveSessionCount(userId));
    
            // Create message
            OrderSuccessMessage message = OrderSuccessMessage.builder()
                    .type("ORDER_SUCCESS")
                    .orderId(orderId)
                    .orderNumber("ORD-001")
                    .status("CONFIRMED")
                    .timestamp(LocalDateTime.now())
                    .message("Your order has been successfully placed!")
                    .build();
    
            // Broadcast (should go to all tabs)
            webSocketHandler.broadcastOrderSuccess(userId, message);
    
            // Verify both sessions received the message
            ArgumentCaptor<TextMessage> messageCaptor1 = ArgumentCaptor.forClass(TextMessage.class);
            ArgumentCaptor<TextMessage> messageCaptor2 = ArgumentCaptor.forClass(TextMessage.class);
    
            verify(mockSession1).sendMessage(messageCaptor1.capture());
            verify(mockSession2).sendMessage(messageCaptor2.capture());
    
            // Parse both messages
            OrderSuccessMessage receivedMessage1 = objectMapper.readValue(
                    messageCaptor1.getValue().getPayload(),
                    OrderSuccessMessage.class
            );
            OrderSuccessMessage receivedMessage2 = objectMapper.readValue(
                    messageCaptor2.getValue().getPayload(),
                    OrderSuccessMessage.class
            );
    
            // Both should have identical content
            assertEquals(receivedMessage1.getOrderId(), receivedMessage2.getOrderId());
            assertEquals(receivedMessage1.getOrderNumber(), receivedMessage2.getOrderNumber());
            assertEquals(receivedMessage1.getAmount(), receivedMessage2.getAmount());
            log.info("[Test 3] ✓ Multi-tab broadcast verified - all tabs received the same message");
        }
    
        @Test
        @DisplayName("Test 4: Session lifecycle - Connect, broadcast, disconnect, verify cleanup")
        void testSessionLifecycle() throws Exception {
            // Setup
            Long userId = 1L;
            Long orderId = 100L;
    
            mockSession1.getAttributes().put("userId", userId);
            when(mockSession1.getId()).thenReturn("session-1");
    
            // User connects
            webSocketHandler.afterConnectionEstablished(mockSession1);
            assertEquals(1, webSocketHandler.getActiveSessionCount(userId));
            log.info("[Test 4.1] ✓ Session established");
    
            // Broadcast message while connected
            OrderSuccessMessage message = OrderSuccessMessage.builder()
                    .type("ORDER_SUCCESS")
                    .orderId(orderId)
                    .orderNumber("ORD-001")
                    .status("CONFIRMED")
                    .timestamp(LocalDateTime.now())
                    .message("Order confirmed!")
                    .build();
    
            webSocketHandler.broadcastOrderSuccess(userId, message);
            verify(mockSession1).sendMessage(any(TextMessage.class));
            log.info("[Test 4.2] ✓ Message broadcasted to connected session");
    
            // User disconnects
            webSocketHandler.afterConnectionClosed(mockSession1, null);
            assertEquals(0, webSocketHandler.getActiveSessionCount(userId));
            log.info("[Test 4.3] ✓ Session disconnected and cleaned up");
    
            // Broadcast after disconnect (should not throw, but no delivery)
            reset(mockSession1);
            webSocketHandler.broadcastOrderSuccess(userId, message);
            verify(mockSession1, never()).sendMessage(any(TextMessage.class));
            log.info("[Test 4.4] ✓ No message sent to disconnected session");
        }
    }
*/
