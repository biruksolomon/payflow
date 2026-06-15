package com.payflow.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * RedisService — central Redis abstraction for the PayFlow auth layer.
 *
 * Responsibilities (all used by AuthService / JwtAuthenticationFilter):
 *  1. JWT blacklisting  — invalidate access tokens on logout / password change
 *  2. Refresh-token store — persist refresh tokens server-side for rotation + revocation
 *  3. Rate-limiting    — cap login / register attempts per IP/email
 *  4. Email-verification token cache — quick lookup without DB hit
 *  5. Generic cache helpers — used by other services later
 *
 * Key namespaces
 *  jwt:blacklist:{token}              → "1"          (TTL = token remaining lifetime)
 *  auth:refresh:{userId}:{tokenHash}  → "1"          (TTL = refresh expiry)
 *  auth:refresh:all:{userId}          → Set<tokenHash> (used for revoke-all)
 *  rate:login:{key}                   → attempt count (TTL = window)
 *  rate:register:{key}                → attempt count (TTL = window)
 *  verify:token:{email}               → verificationToken (TTL = 24h)
 *  cache:{key}                        → arbitrary value
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RedisService {

    private final RedisTemplate<String, String> redisTemplate;

    // ─────────────────────────────────────────────────────────────
    // Key prefixes
    // ─────────────────────────────────────────────────────────────
    private static final String JWT_BLACKLIST_PREFIX   = "jwt:blacklist:";
    private static final String REFRESH_TOKEN_PREFIX   = "auth:refresh:";
    private static final String REFRESH_ALL_PREFIX     = "auth:refresh:all:";
    private static final String RATE_LOGIN_PREFIX      = "rate:login:";
    private static final String RATE_REGISTER_PREFIX   = "rate:register:";
    private static final String VERIFY_TOKEN_PREFIX    = "verify:token:";
    private static final String CACHE_PREFIX           = "cache:";

    // Rate-limit defaults
    private static final int  LOGIN_MAX_ATTEMPTS    = 5;
    private static final long LOGIN_WINDOW_MINUTES  = 15;
    private static final int  REGISTER_MAX_ATTEMPTS = 3;
    private static final long REGISTER_WINDOW_HOURS = 1;

    // ═════════════════════════════════════════════════════════════
    // 1. JWT BLACKLIST
    // ═════════════════════════════════════════════════════════════

    /**
     * Blacklist an access token so it is rejected by JwtAuthenticationFilter
     * even if it hasn't expired yet (logout, password change, account suspension).
     *
     * @param token         raw JWT string
     * @param ttlMillis     remaining lifetime of the token in milliseconds
     */
    public void blacklistToken(String token, long ttlMillis) {
        if (ttlMillis <= 0) {
            log.debug("[Redis] Token already expired — no need to blacklist");
            return;
        }
        String key = JWT_BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "1", Duration.ofMillis(ttlMillis));
        log.debug("[Redis] Token blacklisted (ttl={}ms)", ttlMillis);
    }

    /**
     * Returns true if the token has been explicitly revoked/blacklisted.
     */
    public boolean isTokenBlacklisted(String token) {
        String key = JWT_BLACKLIST_PREFIX + token;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    // ═════════════════════════════════════════════════════════════
    // 2. REFRESH TOKEN STORE
    // ═════════════════════════════════════════════════════════════

    /**
     * Persist a refresh token for a user.
     * We store a hash of the token (not the raw token) to avoid leaking credentials
     * if Redis is compromised.
     *
     * @param userId           owner of the token
     * @param tokenHash        SHA-256 hex of the raw refresh token
     * @param ttlMillis        expiry of the refresh token
     */
    public void storeRefreshToken(Long userId, String tokenHash, long ttlMillis) {
        String singleKey = REFRESH_TOKEN_PREFIX + userId + ":" + tokenHash;
        String setKey    = REFRESH_ALL_PREFIX   + userId;

        redisTemplate.opsForValue().set(singleKey, "1", Duration.ofMillis(ttlMillis));
        // Track all tokens for this user so we can revoke-all
        redisTemplate.opsForSet().add(setKey, tokenHash);
        // Set a generous TTL on the set itself (same as token)
        redisTemplate.expire(setKey, ttlMillis, TimeUnit.MILLISECONDS);
        log.debug("[Redis] Refresh token stored for userId={}", userId);
    }

    /**
     * Validate that the given refresh token hash exists (has not been rotated/revoked).
     */
    public boolean isRefreshTokenValid(Long userId, String tokenHash) {
        String key = REFRESH_TOKEN_PREFIX + userId + ":" + tokenHash;
        Boolean exists = redisTemplate.hasKey(key);
        return Boolean.TRUE.equals(exists);
    }

    /**
     * Invalidate a single refresh token (rotation: old token deleted after issuing new one).
     */
    public void revokeRefreshToken(Long userId, String tokenHash) {
        String singleKey = REFRESH_TOKEN_PREFIX + userId + ":" + tokenHash;
        String setKey    = REFRESH_ALL_PREFIX   + userId;
        redisTemplate.delete(singleKey);
        redisTemplate.opsForSet().remove(setKey, tokenHash);
        log.debug("[Redis] Refresh token revoked for userId={}", userId);
    }

    /**
     * Revoke ALL refresh tokens for a user (logout everywhere / password change).
     */
    public void revokeAllRefreshTokens(Long userId) {
        String setKey = REFRESH_ALL_PREFIX + userId;
        Set<String> hashes = redisTemplate.opsForSet().members(setKey);
        if (hashes != null) {
            for (String hash : hashes) {
                redisTemplate.delete(REFRESH_TOKEN_PREFIX + userId + ":" + hash);
            }
        }
        redisTemplate.delete(setKey);
        log.info("[Redis] All refresh tokens revoked for userId={}", userId);
    }

    // ═════════════════════════════════════════════════════════════
    // 3. RATE LIMITING
    // ═════════════════════════════════════════════════════════════

    /**
     * Increment the login attempt counter for an identifier (email or IP).
     * Returns the new count.  Counter expires after LOGIN_WINDOW_MINUTES.
     */
    public long incrementLoginAttempts(String identifier) {
        String key = RATE_LOGIN_PREFIX + identifier;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            // First attempt — start the window
            redisTemplate.expire(key, LOGIN_WINDOW_MINUTES, TimeUnit.MINUTES);
        }
        log.debug("[Redis] Login attempt #{} for '{}'", count, identifier);
        return count != null ? count : 1;
    }

    /**
     * Returns true when the identifier has exceeded the allowed login attempts.
     */
    public boolean isLoginRateLimited(String identifier) {
        String key = RATE_LOGIN_PREFIX + identifier;
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null) return false;
        return Long.parseLong(raw) >= LOGIN_MAX_ATTEMPTS;
    }

    /**
     * How many seconds until the login rate-limit window resets (0 if not limited).
     */
    public long getLoginRateLimitTtlSeconds(String identifier) {
        String key = RATE_LOGIN_PREFIX + identifier;
        Long ttl = redisTemplate.getExpire(key, TimeUnit.SECONDS);
        return ttl > 0 ? ttl : 0;
    }

    /** Reset login attempt counter on successful authentication. */
    public void resetLoginAttempts(String identifier) {
        redisTemplate.delete(RATE_LOGIN_PREFIX + identifier);
        log.debug("[Redis] Login attempts reset for '{}'", identifier);
    }

    /**
     * Increment the registration attempt counter (per IP) and return new count.
     */
    public long incrementRegisterAttempts(String ipAddress) {
        String key = RATE_REGISTER_PREFIX + ipAddress;
        Long count = redisTemplate.opsForValue().increment(key);
        if (count != null && count == 1) {
            redisTemplate.expire(key, REGISTER_WINDOW_HOURS, TimeUnit.HOURS);
        }
        return count != null ? count : 1;
    }

    /** Returns true when the IP has exceeded allowed registration attempts. */
    public boolean isRegisterRateLimited(String ipAddress) {
        String key = RATE_REGISTER_PREFIX + ipAddress;
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null) return false;
        return Long.parseLong(raw) >= REGISTER_MAX_ATTEMPTS;
    }

    // ═════════════════════════════════════════════════════════════
    // 4. EMAIL VERIFICATION TOKEN CACHE
    // ═════════════════════════════════════════════════════════════

    /**
     * Cache the email verification token so we can validate without a DB hit.
     * TTL matches the 24-hour expiry set in AuthService.
     */
    public void cacheVerificationToken(String email, String token) {
        String key = VERIFY_TOKEN_PREFIX + email;
        redisTemplate.opsForValue().set(key, token, Duration.ofHours(24));
        log.debug("[Redis] Verification token cached for '{}'", email);
    }

    /**
     * Retrieve the cached verification token for an email, if it exists.
     */
    public Optional<String> getVerificationToken(String email) {
        String key   = VERIFY_TOKEN_PREFIX + email;
        String value = redisTemplate.opsForValue().get(key);
        return Optional.ofNullable(value);
    }

    /**
     * Invalidate the cached verification token after successful verification.
     */
    public void evictVerificationToken(String email) {
        redisTemplate.delete(VERIFY_TOKEN_PREFIX + email);
        log.debug("[Redis] Verification token evicted for '{}'", email);
    }

    // ═════════════════════════════════════════════════════════════
    // 5. GENERIC CACHE HELPERS (used by other services)
    // ═════════════════════════════════════════════════════════════

    public void set(String key, String value, Duration ttl) {
        redisTemplate.opsForValue().set(CACHE_PREFIX + key, value, ttl);
    }

    public Optional<String> get(String key) {
        return Optional.ofNullable(redisTemplate.opsForValue().get(CACHE_PREFIX + key));
    }

    public void evict(String key) {
        redisTemplate.delete(CACHE_PREFIX + key);
    }

    public boolean exists(String key) {
        return redisTemplate.hasKey(CACHE_PREFIX + key);
    }
}