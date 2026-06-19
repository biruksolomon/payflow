package com.payflow.backend.security;

import com.payflow.backend.service.RedisService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * JwtAuthenticationFilter — updated to check the Redis blacklist.
 *
 * Extra step vs original:
 *   After signature + expiry are valid, ask RedisService whether the token
 *   has been explicitly revoked (logout, password change, admin suspension).
 *   If blacklisted → treat as unauthenticated (pass through, Spring Security
 *   will return 401 for protected routes).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final RedisService redisService;           // ← injected

    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX        = "Bearer ";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);

            if (StringUtils.hasText(jwt) && jwtTokenProvider.validateToken(jwt)) {

                // ← NEW: reject blacklisted tokens (logged-out sessions)
                if (redisService.isTokenBlacklisted(jwt)) {
                    log.warn("[JwtAuthenticationFilter] Blacklisted token rejected");
                    filterChain.doFilter(request, response);
                    return;
                }

                String username = jwtTokenProvider.getUserNameFromToken(jwt);
                Long   userId   = jwtTokenProvider.getUserIdFromToken(jwt);

                log.debug("[JwtAuthenticationFilter] Validating JWT for user: {} (id: {})", username, userId);

                UserDetails userDetails = userDetailsService.loadUserByUsername(username);

                if (!userDetails.isEnabled()) {
                    log.warn("[JwtAuthenticationFilter] User account is disabled: {}", username);
                    filterChain.doFilter(request, response);
                    return;
                }

                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                userDetails, null, userDetails.getAuthorities());
                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authentication);
                log.debug("[JwtAuthenticationFilter] Authentication set for user: {}", username);

            } else if (StringUtils.hasText(jwt)) {
                log.warn("[JwtAuthenticationFilter] Invalid JWT token detected");
            }
        } catch (Exception e) {
            log.error("[JwtAuthenticationFilter] Error processing JWT token: {}", e.getMessage());
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith(BEARER_PREFIX)) {
            return bearerToken.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}