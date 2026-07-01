/*
    package com.payflow.backend.websocket;

    import com.payflow.backend.security.JwtTokenProvider;
    import org.junit.jupiter.api.BeforeEach;
    import org.junit.jupiter.api.Test;
    import org.junit.jupiter.api.extension.ExtendWith;
    import org.mockito.InjectMocks;
    import org.mockito.Mock;
    import org.mockito.junit.jupiter.MockitoExtension;
    import org.springframework.http.HttpHeaders;
    import org.springframework.http.server.ServerHttpRequest;
    import org.springframework.http.server.ServerHttpResponse;
    import org.springframework.web.socket.WebSocketHandler;

    import java.net.URI;
    import java.util.HashMap;
    import java.util.Map;

    import static org.junit.jupiter.api.Assertions.*;
    import static org.mockito.Mockito.*;

    @ExtendWith(MockitoExtension.class)
    class WebSocketInterceptorTest {

        @Mock
        private JwtTokenProvider jwtProvider;

        @Mock
        private ServerHttpRequest request;

        @Mock
        private ServerHttpResponse response;

        @Mock
        private WebSocketHandler webSocketHandler;

        @InjectMocks
        private WebSocketInterceptor interceptor;

        private Map<String, Object> attributes;

        @BeforeEach
        void setUp() {
            attributes = new HashMap<>();
        }

        // =====================================================
        // beforeHandshake()
        // =====================================================

        @Test
        void beforeHandshake_ShouldAccept_WhenTokenInQueryParameter() throws Exception {

            URI uri = URI.create("ws://localhost/ws/orders?token=test-jwt-token");

            HttpHeaders headers = new HttpHeaders();

            when(request.getURI()).thenReturn(uri);
            when(request.getHeaders()).thenReturn(headers);

            when(jwtProvider.getUserIdFromToken("test-jwt-token"))
                    .thenReturn(1L);

            boolean result = interceptor.beforeHandshake(
                    request,
                    response,
                    webSocketHandler,
                    attributes
            );

            assertTrue(result);
            assertEquals("1", attributes.get("userId"));

            verify(jwtProvider).getUserIdFromToken("test-jwt-token");
        }

        @Test
        void beforeHandshake_ShouldAccept_WhenTokenInAuthorizationHeader() throws Exception {

            URI uri = URI.create("ws://localhost/ws/orders");

            HttpHeaders headers = new HttpHeaders();
            headers.add("Authorization", "Bearer jwt-token");

            when(request.getURI()).thenReturn(uri);
            when(request.getHeaders()).thenReturn(headers);

            when(jwtProvider.getUserIdFromToken("jwt-token"))
                    .thenReturn(10L);

            boolean result = interceptor.beforeHandshake(
                    request,
                    response,
                    webSocketHandler,
                    attributes
            );

            assertTrue(result);
            assertEquals("10", attributes.get("userId"));

            verify(jwtProvider).getUserIdFromToken("jwt-token");
        }

        @Test
        void beforeHandshake_ShouldReject_WhenNoTokenProvided() throws Exception {

            URI uri = URI.create("ws://localhost/ws/orders");

            HttpHeaders headers = new HttpHeaders();

            when(request.getURI()).thenReturn(uri);
            when(request.getHeaders()).thenReturn(headers);

            boolean result = interceptor.beforeHandshake(
                    request,
                    response,
                    webSocketHandler,
                    attributes
            );

            assertFalse(result);

            verify(jwtProvider, never())
                    .getUserIdFromToken(anyString());

            assertTrue(attributes.isEmpty());
        }

        @Test
        void beforeHandshake_ShouldReject_WhenUserIdIsNull() throws Exception {

            URI uri = URI.create("ws://localhost/ws/orders?token=bad-token");

            HttpHeaders headers = new HttpHeaders();

            when(request.getURI()).thenReturn(uri);
            when(request.getHeaders()).thenReturn(headers);

            when(jwtProvider.getUserIdFromToken("bad-token"))
                    .thenReturn(null);

            boolean result = interceptor.beforeHandshake(
                    request,
                    response,
                    webSocketHandler,
                    attributes
            );

            assertFalse(result);

            verify(jwtProvider).getUserIdFromToken("bad-token");

            assertTrue(attributes.isEmpty());
        }

        @Test
        void beforeHandshake_ShouldReject_WhenJwtThrowsException() throws Exception {

            URI uri = URI.create("ws://localhost/ws/orders?token=invalid");

            HttpHeaders headers = new HttpHeaders();

            when(request.getURI()).thenReturn(uri);
            when(request.getHeaders()).thenReturn(headers);

            when(jwtProvider.getUserIdFromToken("invalid"))
                    .thenThrow(new RuntimeException("Invalid token"));

            boolean result = interceptor.beforeHandshake(
                    request,
                    response,
                    webSocketHandler,
                    attributes
            );

            assertFalse(result);

            verify(jwtProvider).getUserIdFromToken("invalid");
        }

        // =====================================================
        // afterHandshake()
        // =====================================================

        @Test
        void afterHandshake_ShouldDoNothing_WhenExceptionIsNull() {

            assertDoesNotThrow(() ->
                    interceptor.afterHandshake(
                            request,
                            response,
                            webSocketHandler,
                            null
                    ));
        }

        @Test
        void afterHandshake_ShouldHandleException() {

            Exception ex = new RuntimeException("Handshake failed");

            assertDoesNotThrow(() ->
                    interceptor.afterHandshake(
                            request,
                            response,
                            webSocketHandler,
                            ex
                    ));
        }

    }*/
