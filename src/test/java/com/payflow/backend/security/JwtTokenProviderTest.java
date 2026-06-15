package com.payflow.backend.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String SECRET =
            "mySuperSecretJwtKeyThatMustBeAtLeastThirtyTwoCharactersLong123";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();

        ReflectionTestUtils.setField(
                jwtTokenProvider,
                "jwtSecret",
                SECRET
        );

        ReflectionTestUtils.setField(
                jwtTokenProvider,
                "jwtExpirationMs",
                3600000L // 1 hour
        );

        ReflectionTestUtils.setField(
                jwtTokenProvider,
                "refreshTokenExpirationMs",
                86400000L // 24 hours
        );
    }

    @Test
    void shouldGenerateAccessToken() {

        String token =
                jwtTokenProvider.generateAccessToken(
                        "user@test.com",
                        1L
                );

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void shouldGenerateRefreshToken() {

        String token =
                jwtTokenProvider.generateRefreshToken(
                        "user@test.com",
                        1L
                );

        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void shouldExtractUsernameFromToken() {

        String token =
                jwtTokenProvider.generateAccessToken(
                        "user@test.com",
                        1L
                );

        String username =
                jwtTokenProvider.getUserNameFromToken(token);

        assertEquals("user@test.com", username);
    }

    @Test
    void shouldExtractUserIdFromToken() {

        String token =
                jwtTokenProvider.generateAccessToken(
                        "user@test.com",
                        99L
                );

        Long userId =
                jwtTokenProvider.getUserIdFromToken(token);

        assertEquals(99L, userId);
    }

    @Test
    void shouldValidateValidToken() {

        String token =
                jwtTokenProvider.generateAccessToken(
                        "user@test.com",
                        1L
                );

        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void shouldReturnFalseForInvalidToken() {

        String invalidToken =
                "this.is.not.a.valid.jwt";

        assertFalse(
                jwtTokenProvider.validateToken(invalidToken)
        );
    }

    @Test
    void shouldDetectExpiredToken() {

        String token =
                jwtTokenProvider.generateTokenWithCustomExpiration(
                        "user@test.com",
                        1L,
                        -1000L
                );

        assertTrue(
                jwtTokenProvider.isTokenExpired(token)
        );
    }

    @Test
    void shouldReturnFalseForNonExpiredToken() {

        String token =
                jwtTokenProvider.generateAccessToken(
                        "user@test.com",
                        1L
                );

        assertFalse(
                jwtTokenProvider.isTokenExpired(token)
        );
    }

    @Test
    void shouldGenerateTokenUsingAuthentication() {

        PayFlowUserDetails userDetails =
                org.mockito.Mockito.mock(
                        PayFlowUserDetails.class
                );

        org.mockito.Mockito.when(userDetails.getUsername())
                .thenReturn("user@test.com");

        org.mockito.Mockito.when(userDetails.getId())
                .thenReturn(1L);

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );

        String token =
                jwtTokenProvider.generateAccessToken(
                        authentication
                );

        assertNotNull(token);

        assertEquals(
                "user@test.com",
                jwtTokenProvider.getUserNameFromToken(token)
        );

        assertEquals(
                1L,
                jwtTokenProvider.getUserIdFromToken(token)
        );
    }

    @Test
    void shouldTreatMalformedTokenAsExpired() {

        assertTrue(
                jwtTokenProvider.isTokenExpired(
                        "invalid.token"
                )
        );
    }

    @Test
    void shouldReturnFalseWhenTokenSignedWithDifferentSecret() {

        JwtTokenProvider anotherProvider =
                new JwtTokenProvider();

        ReflectionTestUtils.setField(
                anotherProvider,
                "jwtSecret",
                "anotherSecretKeyThatMustBeAtLeastThirtyTwoCharactersLong123"
        );

        ReflectionTestUtils.setField(
                anotherProvider,
                "jwtExpirationMs",
                3600000L
        );

        ReflectionTestUtils.setField(
                anotherProvider,
                "refreshTokenExpirationMs",
                86400000L
        );

        String token =
                anotherProvider.generateAccessToken(
                        "user@test.com",
                        1L
                );

        assertFalse(
                jwtTokenProvider.validateToken(token)
        );
    }
}