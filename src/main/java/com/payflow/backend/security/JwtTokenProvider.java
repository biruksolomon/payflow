package com.payflow.backend.security;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import io.jsonwebtoken.security.SignatureException;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

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
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expirationMs);

        return Jwts.builder()
                .subject(username)
                .claim("userId", userId)
                .issuedAt(now)
                .expiration(expiryDate)
                .signWith(key)
                .compact();
    }

    public Long getUserIdFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get("userId", Long.class);
    }

    public String getUserNameFromToken(String token) {
        SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
        Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }

    public boolean validateToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return true;
        } catch (SignatureException | SecurityException | MalformedJwtException e){
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
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return claims.getExpiration().before(new Date());
        } catch (ExpiredJwtException e) {
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            log.error("[JwtTokenProvider] Error checking token expiration: {}", e.getMessage());
            return true;
        }
    }
}

