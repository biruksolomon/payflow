package com.payflow.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JwtTokenProvider — generates and validates JWTs.
 *
 * Added vs original:
 *  - getRemainingExpirationMs(token)  needed by AuthService to set blacklist TTL
 *  - getExpirationDate(token)         helper for the above
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret-key}")
    private String jwtSecret;

    @Value("${app.jwt.expiration}")
    private long jwtExpirationMs;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    // ─────────────────────────────────────────────────────────────
    // Token generation
    // ─────────────────────────────────────────────────────────────

    public String generateAccessToken(Authentication authentication) {
        PayFlowUserDetails userPrincipal = (PayFlowUserDetails) authentication.getPrincipal();
        return generateAccessToken(userPrincipal.getUsername(), userPrincipal.getId());
    }

    public String generateAccessToken(String username, Long userId) {
        return buildToken(username, userId, jwtExpirationMs);
    }

    public String generateRefreshToken(String username, Long userId) {
        return buildToken(username, userId, refreshTokenExpirationMs);
    }

    public String generateTokenWithCustomExpiration(String username, Long userId, long expirationMs) {
        return buildToken(username, userId, expirationMs);
    }

    private String buildToken(String username, Long userId, long expirationMs) {
        SecretKey key = secretKey();
        Date now        = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    // ─────────────────────────────────────────────────────────────
    // Token parsing
    // ─────────────────────────────────────────────────────────────

    public Long getUserIdFromToken(String token) {
        Claims claims = parseClaims(token);
        return claims.get("userId", Long.class);
    }

    public String getUserNameFromToken(String token) {
        return parseClaims(token).getSubject();
    }

    /**
     * Returns the number of milliseconds remaining until the token expires.
     * Returns 0 if the token is already expired or unparseable.
     * Used by AuthService to set the Redis blacklist TTL precisely.
     */
    public long getRemainingExpirationMs(String token) {
        try {
            Date expiration = parseClaims(token).getExpiration();
            long remaining  = expiration.getTime() - System.currentTimeMillis();
            return Math.max(0, remaining);
        } catch (ExpiredJwtException e) {
            return 0;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("[JwtTokenProvider] Could not parse token for expiry: {}", e.getMessage());
            return 0;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Validation
    // ─────────────────────────────────────────────────────────────

    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                    .verifyWith(secretKey())
                    .build()
                    .parseSignedClaims(token);
            return true;
        } catch (SignatureException | SecurityException | MalformedJwtException e) {
            log.error("[JwtTokenProvider] Invalid JWT signature: {}", e.getMessage());
        } catch (ExpiredJwtException e) {
            log.error("[JwtTokenProvider] Expired JWT token: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.error("[JwtTokenProvider] Unsupported JWT token: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.error("[JwtTokenProvider] JWT claims string is empty: {}", e.getMessage());
        }
        return false;
    }

    public boolean isTokenExpired(String token) {
        try {
            return parseClaims(token).getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("[JwtTokenProvider] Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Private helpers
    // ─────────────────────────────────────────────────────────────

    private SecretKey secretKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(secretKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}