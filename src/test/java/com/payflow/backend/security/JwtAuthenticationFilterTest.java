package com.payflow.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private UserDetailsService userDetailsService;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @Mock
    private UserDetails userDetails;

    @InjectMocks
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    private static final String TOKEN = "jwt-token";
    private static final String EMAIL = "user@test.com";

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void shouldAuthenticateUserWhenTokenIsValid()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + TOKEN);

        when(jwtTokenProvider.validateToken(TOKEN))
                .thenReturn(true);

        when(jwtTokenProvider.getUserNameFromToken(TOKEN))
                .thenReturn(EMAIL);

        when(jwtTokenProvider.getUserIdFromToken(TOKEN))
                .thenReturn(1L);

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(userDetails);

        when(userDetails.isEnabled())
                .thenReturn(true);

        when(userDetails.getAuthorities())
                .thenReturn(Collections.emptyList());

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNotNull(
                SecurityContextHolder.getContext().getAuthentication()
        );

        assertEquals(
                userDetails,
                SecurityContextHolder
                        .getContext()
                        .getAuthentication()
                        .getPrincipal()
        );

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateWhenTokenIsInvalid()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + TOKEN);

        when(jwtTokenProvider.validateToken(TOKEN))
                .thenReturn(false);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );

        verify(filterChain).doFilter(request, response);

        verify(userDetailsService, never())
                .loadUserByUsername(anyString());
    }

    @Test
    void shouldNotAuthenticateWhenAuthorizationHeaderMissing()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn(null);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldNotAuthenticateDisabledUser()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + TOKEN);

        when(jwtTokenProvider.validateToken(TOKEN))
                .thenReturn(true);

        when(jwtTokenProvider.getUserNameFromToken(TOKEN))
                .thenReturn(EMAIL);

        when(jwtTokenProvider.getUserIdFromToken(TOKEN))
                .thenReturn(1L);

        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenReturn(userDetails);

        when(userDetails.isEnabled())
                .thenReturn(false);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueFilterChainWhenExceptionOccurs()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn("Bearer " + TOKEN);

        when(jwtTokenProvider.validateToken(TOKEN))
                .thenThrow(new RuntimeException("JWT error"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldIgnoreHeaderWithoutBearerPrefix()
            throws ServletException, IOException {

        when(request.getHeader("Authorization"))
                .thenReturn(TOKEN);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(
                SecurityContextHolder.getContext().getAuthentication()
        );

        verify(filterChain).doFilter(request, response);

        verify(jwtTokenProvider, never())
                .validateToken(anyString());
    }
}