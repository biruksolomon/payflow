package com.payflow.backend.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RedisServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private RedisService redisService;

    // =========================================================
    // JWT BLACKLIST
    // =========================================================

    @Test
    void shouldBlacklistToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        redisService.blacklistToken("jwt-token", 5000);

        verify(valueOperations)
                .set(
                        eq("jwt:blacklist:jwt-token"),
                        eq("1"),
                        eq(Duration.ofMillis(5000))
                );
    }

    @Test
    void shouldNotBlacklistExpiredToken() {
        redisService.blacklistToken("jwt-token", 0);

        verify(valueOperations, never())
                .set(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void shouldReturnTrueWhenTokenBlacklisted() {
        when(redisTemplate.hasKey("jwt:blacklist:token"))
                .thenReturn(true);

        assertTrue(
                redisService.isTokenBlacklisted("token")
        );
    }

    @Test
    void shouldReturnFalseWhenTokenNotBlacklisted() {
        when(redisTemplate.hasKey("jwt:blacklist:token"))
                .thenReturn(false);

        assertFalse(
                redisService.isTokenBlacklisted("token")
        );
    }

    // =========================================================
    // REFRESH TOKENS
    // =========================================================

    @Test
    void shouldStoreRefreshToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        redisService.storeRefreshToken(
                1L,
                "hash",
                10000L
        );

        verify(valueOperations)
                .set(
                        eq("auth:refresh:1:hash"),
                        eq("1"),
                        eq(Duration.ofMillis(10000))
                );

        verify(setOperations)
                .add("auth:refresh:all:1", "hash");

        verify(redisTemplate)
                .expire(
                        "auth:refresh:all:1",
                        10000L,
                        TimeUnit.MILLISECONDS
                );
    }

    @Test
    void shouldValidateRefreshToken() {
        when(redisTemplate.hasKey("auth:refresh:1:hash"))
                .thenReturn(true);

        assertTrue(
                redisService.isRefreshTokenValid(
                        1L,
                        "hash"
                )
        );
    }

    @Test
    void shouldRevokeRefreshToken() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);

        redisService.revokeRefreshToken(
                1L,
                "hash"
        );

        verify(redisTemplate)
                .delete("auth:refresh:1:hash");

        verify(setOperations)
                .remove("auth:refresh:all:1", "hash");
    }

    @Test
    void shouldRevokeAllRefreshTokens() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("auth:refresh:all:1"))
                .thenReturn(Set.of("a", "b"));

        redisService.revokeAllRefreshTokens(1L);

        verify(redisTemplate)
                .delete("auth:refresh:1:a");

        verify(redisTemplate)
                .delete("auth:refresh:1:b");

        verify(redisTemplate)
                .delete("auth:refresh:all:1");
    }

    @Test
    void shouldHandleNullRefreshTokenSet() {
        when(redisTemplate.opsForSet()).thenReturn(setOperations);
        when(setOperations.members("auth:refresh:all:1"))
                .thenReturn(null);

        redisService.revokeAllRefreshTokens(1L);

        verify(redisTemplate)
                .delete("auth:refresh:all:1");
    }

    // =========================================================
    // LOGIN RATE LIMITING
    // =========================================================

    @Test
    void shouldIncrementLoginAttemptsFirstTime() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("rate:login:user@test.com"))
                .thenReturn(1L);

        long count =
                redisService.incrementLoginAttempts(
                        "user@test.com"
                );

        assertEquals(1L, count);

        verify(redisTemplate)
                .expire(
                        "rate:login:user@test.com",
                        15,
                        TimeUnit.MINUTES
                );
    }

    @Test
    void shouldIncrementLoginAttempts() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("rate:login:user@test.com"))
                .thenReturn(3L);

        long count =
                redisService.incrementLoginAttempts(
                        "user@test.com"
                );

        assertEquals(3L, count);
    }

    @Test
    void shouldDetectLoginRateLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rate:login:user@test.com"))
                .thenReturn("5");

        assertTrue(
                redisService.isLoginRateLimited(
                        "user@test.com"
                )
        );
    }

    @Test
    void shouldReturnFalseWhenLoginNotLimited() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rate:login:user@test.com"))
                .thenReturn("2");

        assertFalse(
                redisService.isLoginRateLimited(
                        "user@test.com"
                )
        );
    }

    @Test
    void shouldReturnFalseWhenLoginCounterMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rate:login:user@test.com"))
                .thenReturn(null);

        assertFalse(
                redisService.isLoginRateLimited(
                        "user@test.com"
                )
        );
    }

    @Test
    void shouldReturnLoginRateLimitTtl() {
        when(redisTemplate.getExpire(
                "rate:login:user@test.com",
                TimeUnit.SECONDS
        )).thenReturn(120L);

        assertEquals(
                120L,
                redisService.getLoginRateLimitTtlSeconds(
                        "user@test.com"
                )
        );
    }

    @Test
    void shouldReturnZeroWhenTtlNegative() {
        when(redisTemplate.getExpire(
                "rate:login:user@test.com",
                TimeUnit.SECONDS
        )).thenReturn(-1L);

        assertEquals(
                0L,
                redisService.getLoginRateLimitTtlSeconds(
                        "user@test.com"
                )
        );
    }

    @Test
    void shouldResetLoginAttempts() {
        redisService.resetLoginAttempts(
                "user@test.com"
        );

        verify(redisTemplate)
                .delete("rate:login:user@test.com");
    }

    // =========================================================
    // REGISTER RATE LIMITING
    // =========================================================

    @Test
    void shouldIncrementRegisterAttempts() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment("rate:register:127.0.0.1"))
                .thenReturn(1L);

        long count =
                redisService.incrementRegisterAttempts(
                        "127.0.0.1"
                );

        assertEquals(1L, count);

        verify(redisTemplate)
                .expire(
                        "rate:register:127.0.0.1",
                        1,
                        TimeUnit.HOURS
                );
    }

    @Test
    void shouldDetectRegisterRateLimit() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rate:register:127.0.0.1"))
                .thenReturn("3");

        assertTrue(
                redisService.isRegisterRateLimited(
                        "127.0.0.1"
                )
        );
    }

    @Test
    void shouldReturnFalseWhenRegisterNotLimited() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("rate:register:127.0.0.1"))
                .thenReturn("1");

        assertFalse(
                redisService.isRegisterRateLimited(
                        "127.0.0.1"
                )
        );
    }

    // =========================================================
    // EMAIL VERIFICATION
    // =========================================================

    @Test
    void shouldCacheVerificationToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        redisService.cacheVerificationToken(
                "user@test.com",
                "token123"
        );

        verify(valueOperations)
                .set(
                        eq("verify:token:user@test.com"),
                        eq("token123"),
                        eq(Duration.ofHours(24))
                );
    }

    @Test
    void shouldGetVerificationToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(
                "verify:token:user@test.com"
        )).thenReturn("token123");

        Optional<String> token =
                redisService.getVerificationToken(
                        "user@test.com"
                );

        assertTrue(token.isPresent());
        assertEquals("token123", token.get());
    }

    @Test
    void shouldReturnEmptyVerificationToken() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(
                "verify:token:user@test.com"
        )).thenReturn(null);

        assertTrue(
                redisService.getVerificationToken(
                        "user@test.com"
                ).isEmpty()
        );
    }

    @Test
    void shouldEvictVerificationToken() {
        redisService.evictVerificationToken(
                "user@test.com"
        );

        verify(redisTemplate)
                .delete("verify:token:user@test.com");
    }

    // =========================================================
    // GENERIC CACHE
    // =========================================================

    @Test
    void shouldSetCacheValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        redisService.set(
                "key",
                "value",
                Duration.ofMinutes(5)
        );

        verify(valueOperations)
                .set(
                        "cache:key",
                        "value",
                        Duration.ofMinutes(5)
                );
    }

    @Test
    void shouldGetCacheValue() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cache:key"))
                .thenReturn("value");

        Optional<String> result =
                redisService.get("key");

        assertTrue(result.isPresent());
        assertEquals("value", result.get());
    }

    @Test
    void shouldEvictCacheValue() {
        redisService.evict("key");

        verify(redisTemplate)
                .delete("cache:key");
    }

    @Test
    void shouldCheckCacheExists() {
        when(redisTemplate.hasKey("cache:key"))
                .thenReturn(true);

        assertTrue(
                redisService.exists("key")
        );
    }
}