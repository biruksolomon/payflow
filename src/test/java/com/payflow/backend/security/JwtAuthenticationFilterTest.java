package com.payflow.backend.security;

import com.payflow.backend.service.RedisService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthenticationFilterTest {

    @Mock private JwtTokenProvider     jwtTokenProvider;
    @Mock private UserDetailsService   userDetailsService;
    @Mock private RedisService         redisService;        // ← required by new filter
    @Mock private HttpServletRequest   request;
    @Mock private HttpServletResponse  response;
    @Mock private FilterChain          filterChain;
    @Mock private UserDetails          userDetails;

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

    // ─────────────────────────────────────────────────────────────
    // HAPPY PATH
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldAuthenticateUserWhenTokenIsValid()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(redisService.isTokenBlacklisted(TOKEN)).thenReturn(false);
        when(jwtTokenProvider.getUserNameFromToken(TOKEN)).thenReturn(EMAIL);
        when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(1L);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        when(userDetails.isEnabled()).thenReturn(true);
        when(userDetails.getAuthorities()).thenReturn(Collections.emptyList());

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNotNull(SecurityContextHolder.getContext().getAuthentication());
        assertEquals(userDetails,
                SecurityContextHolder.getContext().getAuthentication().getPrincipal());
        verify(filterChain).doFilter(request, response);
    }

    // ─────────────────────────────────────────────────────────────
    // BLACKLISTED TOKEN  (new behaviour)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldRejectBlacklistedToken()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(redisService.isTokenBlacklisted(TOKEN)).thenReturn(true);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        // Filter chain must still continue (Spring Security handles the 401)
        verify(filterChain).doFilter(request, response);
        // Authentication must NOT be set
        assertNull(SecurityContextHolder.getContext().getAuthentication());
        // User lookup must NOT happen after a blacklist hit
        verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    @Test
    void shouldCheckBlacklistBeforeLoadingUser()
            throws ServletException, IOException {
        // Strict ordering: blacklist check happens before userDetailsService
        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(redisService.isTokenBlacklisted(TOKEN)).thenReturn(true);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        var inOrder = inOrder(redisService, userDetailsService);
        inOrder.verify(redisService).isTokenBlacklisted(TOKEN);
        inOrder.verify(userDetailsService, never()).loadUserByUsername(anyString());
    }

    // ─────────────────────────────────────────────────────────────
    // INVALID / MISSING TOKEN
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldNotAuthenticateWhenTokenIsInvalid()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(false);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verify(userDetailsService, never()).loadUserByUsername(anyString());
        // Blacklist check must not be called when the token is already invalid
        verify(redisService, never()).isTokenBlacklisted(anyString());
    }

    @Test
    void shouldNotAuthenticateWhenAuthorizationHeaderMissing()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn(null);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider, never()).validateToken(anyString());
    }

    @Test
    void shouldIgnoreHeaderWithoutBearerPrefix()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn(TOKEN);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
        verify(jwtTokenProvider, never()).validateToken(anyString());
        verify(redisService,     never()).isTokenBlacklisted(anyString());
    }

    // ─────────────────────────────────────────────────────────────
    // DISABLED USER
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldNotAuthenticateDisabledUser()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(redisService.isTokenBlacklisted(TOKEN)).thenReturn(false);
        when(jwtTokenProvider.getUserNameFromToken(TOKEN)).thenReturn(EMAIL);
        when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(1L);
        when(userDetailsService.loadUserByUsername(EMAIL)).thenReturn(userDetails);
        when(userDetails.isEnabled()).thenReturn(false);

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    // ─────────────────────────────────────────────────────────────
    // EXCEPTION SAFETY
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldContinueFilterChainWhenJwtValidationThrows()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN))
                .thenThrow(new RuntimeException("JWT error"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueFilterChainWhenRedisThrows()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(redisService.isTokenBlacklisted(TOKEN))
                .thenThrow(new RuntimeException("Redis down"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void shouldContinueFilterChainWhenUserDetailsThrows()
            throws ServletException, IOException {

        when(request.getHeader("Authorization")).thenReturn("Bearer " + TOKEN);
        when(jwtTokenProvider.validateToken(TOKEN)).thenReturn(true);
        when(redisService.isTokenBlacklisted(TOKEN)).thenReturn(false);
        when(jwtTokenProvider.getUserNameFromToken(TOKEN)).thenReturn(EMAIL);
        when(jwtTokenProvider.getUserIdFromToken(TOKEN)).thenReturn(1L);
        when(userDetailsService.loadUserByUsername(EMAIL))
                .thenThrow(new RuntimeException("DB down"));

        jwtAuthenticationFilter.doFilter(request, response, filterChain);

        assertNull(SecurityContextHolder.getContext().getAuthentication());
        verify(filterChain).doFilter(request, response);
    }
}