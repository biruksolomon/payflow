/*
package com.payflow.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.websocket.dto.OrderSuccessMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class OrderWebSocketHandlerTest {

    private OrderWebSocketHandler handler;

    private WebSocketSession session;

    @BeforeEach
    void setUp() {
        handler = new OrderWebSocketHandler();
        session = mock(WebSocketSession.class);
    }

    // ============================================================
    // afterConnectionEstablished()
    // ============================================================

    @Test
    void shouldRegisterUserSession() throws Exception {

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "1");

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("SESSION-1");

        handler.afterConnectionEstablished(session);

        assertThat(handler.getActiveSessionCount(1L)).isEqualTo(1);
    }

    @Test
    void shouldCloseConnectionWhenUserIdMissing() throws Exception {

        when(session.getAttributes()).thenReturn(new HashMap<>());

        handler.afterConnectionEstablished(session);

        verify(session).close();
    }

    // ============================================================
    // afterConnectionClosed()
    // ============================================================

    @Test
    void shouldRemoveSessionWhenClosed() throws Exception {

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "5");

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("SESSION-5");

        handler.afterConnectionEstablished(session);

        assertThat(handler.getActiveSessionCount(5L)).isEqualTo(1);

        handler.afterConnectionClosed(session, CloseStatus.NORMAL);

        assertThat(handler.getActiveSessionCount(5L)).isZero();
    }

    // ============================================================
    // handleTextMessage()
    // ============================================================

    @Test
    void shouldHandleIncomingMessage() throws Exception {

        TextMessage message = new TextMessage("hello");

        handler.handleTextMessage(session, message);

        // nothing to verify
    }

    // ============================================================
    // broadcastOrderSuccess()
    // ============================================================

    @Test
    void shouldBroadcastMessageToConnectedUser() throws Exception {

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "100");

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("SESSION-100");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        OrderSuccessMessage message = mock(OrderSuccessMessage.class);

        handler.broadcastOrderSuccess(100L, message);

        verify(session).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldNotSendWhenSessionClosed() throws Exception {

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "101");

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("SESSION-101");
        when(session.isOpen()).thenReturn(false);

        handler.afterConnectionEstablished(session);

        handler.broadcastOrderSuccess(101L, mock(OrderSuccessMessage.class));

        verify(session, never()).sendMessage(any(TextMessage.class));
    }

    @Test
    void shouldIgnoreWhenNoSessionsExist() {

        handler.broadcastOrderSuccess(999L, mock(OrderSuccessMessage.class));

        assertThat(handler.getActiveSessionCount(999L)).isZero();
    }

    @Test
    void shouldContinueWhenIOExceptionOccurs() throws Exception {

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "50");

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("SESSION-50");
        when(session.isOpen()).thenReturn(true);

        doThrow(new IOException("network"))
                .when(session)
                .sendMessage(any(TextMessage.class));

        handler.afterConnectionEstablished(session);

        handler.broadcastOrderSuccess(50L, mock(OrderSuccessMessage.class));

        verify(session).sendMessage(any(TextMessage.class));
    }

    // ============================================================
    // getActiveSessionCount()
    // ============================================================

    @Test
    void shouldReturnZeroWhenUserHasNoSessions() {

        assertThat(handler.getActiveSessionCount(123L)).isZero();
    }

    @Test
    void shouldReturnCorrectSessionCount() throws Exception {

        WebSocketSession session1 = mock(WebSocketSession.class);
        WebSocketSession session2 = mock(WebSocketSession.class);

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "88");

        when(session1.getAttributes()).thenReturn(attributes);
        when(session2.getAttributes()).thenReturn(attributes);

        when(session1.getId()).thenReturn("S1");
        when(session2.getId()).thenReturn("S2");

        handler.afterConnectionEstablished(session1);
        handler.afterConnectionEstablished(session2);

        assertThat(handler.getActiveSessionCount(88L)).isEqualTo(2);
    }

    // ============================================================
    // Exception while serializing
    // ============================================================

    @Test
    void shouldHandleSerializationExceptionGracefully() throws Exception {

        Map<String, Object> attributes = new HashMap<>();
        attributes.put("userId", "44");

        when(session.getAttributes()).thenReturn(attributes);
        when(session.getId()).thenReturn("SESSION-44");
        when(session.isOpen()).thenReturn(true);

        handler.afterConnectionEstablished(session);

        ObjectMapper badMapper = mock(com.fasterxml.jackson.databind.ObjectMapper.class);

        doThrow(new RuntimeException("serialization"))
                .when(badMapper)
                .writeValueAsString(any());

        ReflectionTestUtils.setField(handler, "objectMapper", badMapper);

        handler.broadcastOrderSuccess(44L, mock(OrderSuccessMessage.class));

        verify(session, never()).sendMessage(any());
    }

}*/
