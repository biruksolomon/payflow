package com.payflow.backend.service;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.dto.request.AuthRequest;
import com.payflow.backend.dto.response.AuthResponse;
import com.payflow.backend.dto.request.RegisterRequest;
import com.payflow.backend.exception.*;
import com.payflow.backend.repository.UserRepository;
import com.payflow.backend.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock private UserRepository       userRepository;
    @Mock private PasswordEncoder      passwordEncoder;
    @Mock private AuthenticationManager authenticationManager;
    @Mock private JwtTokenProvider     jwtTokenProvider;
    @Mock private EmailService         emailService;
    @Mock private RedisService         redisService;
    @Mock private HttpServletRequest   httpServletRequest;

    @InjectMocks
    private AuthService authService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("user@test.com")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("encodedPassword")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
    }

    // =====================================================
    // REGISTER
    // =====================================================

    @Test
    void shouldRegisterSuccessfully() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("password123");
        request.setPasswordConfirm("password123");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(redisService.isRegisterRateLimited("127.0.0.1")).thenReturn(false);
        when(userRepository.existsByEmail("user@test.com")).thenReturn(false);
        when(passwordEncoder.encode("password123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any(Authentication.class))).thenReturn("access-token");
        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong())).thenReturn("refresh-token");

        AuthResponse response = authService.register(request, httpServletRequest);

        assertNotNull(response);
        assertEquals("access-token",  response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());

        verify(redisService).incrementRegisterAttempts("127.0.0.1");
        verify(redisService).cacheVerificationToken(eq("user@test.com"), anyString());
        verify(redisService).storeRefreshToken(eq(1L), anyString(), anyLong());
        verify(emailService).sendEmailVerification(eq("user@test.com"), anyString());
    }

    @Test
    void shouldRegisterUsingXForwardedForIp() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("pass");
        request.setPasswordConfirm("pass");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn("10.0.0.1, 10.0.0.2");
        when(redisService.isRegisterRateLimited("10.0.0.1")).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });
        when(jwtTokenProvider.generateAccessToken(any())).thenReturn("at");
        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong())).thenReturn("rt");

        AuthResponse response = authService.register(request, httpServletRequest);

        assertNotNull(response);
        verify(redisService).isRegisterRateLimited("10.0.0.1");
        verify(redisService).incrementRegisterAttempts("10.0.0.1");
    }

    @Test
    void shouldThrowWhenRegisterRateLimited() {
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("1.2.3.4");
        when(redisService.isRegisterRateLimited("1.2.3.4")).thenReturn(true);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("pass");
        request.setPasswordConfirm("pass");

        assertThrows(AuthException.class,
                () -> authService.register(request, httpServletRequest));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPasswordsDoNotMatch() {
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(redisService.isRegisterRateLimited("127.0.0.1")).thenReturn(false);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("123");
        request.setPasswordConfirm("456");

        assertThrows(PasswordMismatchException.class,
                () -> authService.register(request, httpServletRequest));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenEmailAlreadyExists() {
        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(redisService.isRegisterRateLimited("127.0.0.1")).thenReturn(false);
        when(userRepository.existsByEmail("user@test.com")).thenReturn(true);

        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("123");
        request.setPasswordConfirm("123");

        assertThrows(DuplicateEmailException.class,
                () -> authService.register(request, httpServletRequest));
    }

    @Test
    void shouldContinueRegistrationWhenEmailFails() {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("123456");
        request.setPasswordConfirm("123456");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(httpServletRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpServletRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpServletRequest.getRemoteAddr()).thenReturn("127.0.0.1");
        when(redisService.isRegisterRateLimited("127.0.0.1")).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenReturn(user);
        when(jwtTokenProvider.generateAccessToken(any(Authentication.class))).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong())).thenReturn("refresh");
        doThrow(new RuntimeException("Mail Error"))
                .when(emailService).sendEmailVerification(anyString(), anyString());

        AuthResponse response = authService.register(request, httpServletRequest);

        assertNotNull(response);   // email failure must not abort registration
    }

    // =====================================================
    // LOGIN
    // =====================================================

    @Test
    void shouldLoginSuccessfully() {
        AuthRequest request = new AuthRequest();
        request.setEmail("user@test.com");
        request.setPassword("password");

        when(redisService.isLoginRateLimited("user@test.com")).thenReturn(false);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                "user@test.com", "password");
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findActiveByEmail("user@test.com")).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(authentication)).thenReturn("access");
        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong())).thenReturn("refresh");

        AuthResponse response = authService.login(request, httpServletRequest);

        assertEquals("access", response.getAccessToken());
        verify(redisService).resetLoginAttempts("user@test.com");
        verify(redisService).storeRefreshToken(eq(1L), anyString(), anyLong());
    }

    @Test
    void shouldThrowWhenLoginRateLimited() {
        AuthRequest request = new AuthRequest();
        request.setEmail("user@test.com");
        request.setPassword("password");

        when(redisService.isLoginRateLimited("user@test.com")).thenReturn(true);
        when(redisService.getLoginRateLimitTtlSeconds("user@test.com")).thenReturn(300L);

        assertThrows(AuthException.class,
                () -> authService.login(request, httpServletRequest));

        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    void shouldThrowWhenCredentialsInvalid() {
        AuthRequest request = new AuthRequest();
        request.setEmail("user@test.com");
        request.setPassword("wrong");

        when(redisService.isLoginRateLimited("user@test.com")).thenReturn(false);
        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Invalid"));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(request, httpServletRequest));

        // Counter must be incremented on bad credentials
        verify(redisService).incrementLoginAttempts("user@test.com");
    }

    @Test
    void shouldThrowWhenUserNotFoundAfterAuthentication() {
        AuthRequest request = new AuthRequest();
        request.setEmail("user@test.com");
        request.setPassword("password");

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword());

        when(redisService.isLoginRateLimited("user@test.com")).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findActiveByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(RuntimeException.class,
                () -> authService.login(request, httpServletRequest));
    }

    @Test
    void shouldThrowWhenAccountInactive() {
        user.setAccountStatus(AccountStatus.SUSPENDED);

        AuthRequest request = new AuthRequest();
        request.setEmail(user.getEmail());
        request.setPassword("password");

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword());

        when(redisService.isLoginRateLimited("user@test.com")).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findActiveByEmail(anyString())).thenReturn(Optional.of(user));

        assertThrows(InvalidCredentialsException.class,
                () -> authService.login(request, httpServletRequest));
    }

    @Test
    void shouldThrowWhenEmailNotVerified() {
        user.setEmailVerified(false);

        AuthRequest request = new AuthRequest();
        request.setEmail(user.getEmail());
        request.setPassword("password");

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                request.getEmail(), request.getPassword());

        when(redisService.isLoginRateLimited("user@test.com")).thenReturn(false);
        when(authenticationManager.authenticate(any())).thenReturn(authentication);
        when(userRepository.findActiveByEmail(anyString())).thenReturn(Optional.of(user));

        assertThrows(AuthException.class,
                () -> authService.login(request, httpServletRequest));
    }

    // =====================================================
    // LOGOUT
    // =====================================================

    @Test
    void shouldLogoutSuccessfully() {
        when(jwtTokenProvider.getRemainingExpirationMs("access-token")).thenReturn(60_000L);

        authService.logout("access-token", "refresh-token", 1L);

        verify(redisService).blacklistToken("access-token", 60_000L);
        verify(redisService).revokeRefreshToken(eq(1L), anyString());
    }

    @Test
    void shouldLogoutWithoutRefreshToken() {
        when(jwtTokenProvider.getRemainingExpirationMs("access-token")).thenReturn(60_000L);

        authService.logout("access-token", null, 1L);

        verify(redisService).blacklistToken("access-token", 60_000L);
        verify(redisService, never()).revokeRefreshToken(anyLong(), anyString());
    }

    @Test
    void shouldLogoutWithBlankRefreshToken() {
        when(jwtTokenProvider.getRemainingExpirationMs("access-token")).thenReturn(5_000L);

        authService.logout("access-token", "   ", 1L);

        verify(redisService).blacklistToken("access-token", 5_000L);
        verify(redisService, never()).revokeRefreshToken(anyLong(), anyString());
    }

    // =====================================================
    // LOGOUT ALL
    // =====================================================

    @Test
    void shouldLogoutAllSuccessfully() {
        when(jwtTokenProvider.getRemainingExpirationMs("access-token")).thenReturn(60_000L);

        authService.logoutAll("access-token", 1L);

        verify(redisService).blacklistToken("access-token", 60_000L);
        verify(redisService).revokeAllRefreshTokens(1L);
    }

    @Test
    void shouldLogoutAllEvenWhenAccessTokenAlreadyExpired() {
        when(jwtTokenProvider.getRemainingExpirationMs("expired-token")).thenReturn(0L);

        authService.logoutAll("expired-token", 1L);

        // blacklistToken is called; RedisService itself skips storing 0-TTL entries
        verify(redisService).blacklistToken("expired-token", 0L);
        verify(redisService).revokeAllRefreshTokens(1L);
    }

    // =====================================================
    // REFRESH TOKEN
    // =====================================================

    @Test
    void shouldRefreshTokenSuccessfully() {
        when(jwtTokenProvider.validateToken("refresh")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("refresh")).thenReturn(1L);
        when(redisService.isRefreshTokenValid(eq(1L), anyString())).thenReturn(true);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.generateAccessToken(any(Authentication.class))).thenReturn("new-access");
        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong())).thenReturn("new-refresh");

        AuthResponse response = authService.refreshToken("refresh");

        assertEquals("new-access",  response.getAccessToken());
        assertEquals("new-refresh", response.getRefreshToken());

        // Old token must be revoked (rotation)
        verify(redisService).revokeRefreshToken(eq(1L), anyString());
        // New token must be stored
        verify(redisService).storeRefreshToken(eq(1L), anyString(), anyLong());
    }

    @Test
    void shouldThrowWhenRefreshTokenInvalid() {
        when(jwtTokenProvider.validateToken("bad-token")).thenReturn(false);

        assertThrows(AuthException.class, () -> authService.refreshToken("bad-token"));
    }

    @Test
    void shouldThrowWhenRefreshTokenRevokedInRedis() {
        when(jwtTokenProvider.validateToken("refresh")).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken("refresh")).thenReturn(1L);
        when(redisService.isRefreshTokenValid(eq(1L), anyString())).thenReturn(false);

        assertThrows(AuthException.class, () -> authService.refreshToken("refresh"));

        verify(userRepository, never()).findActiveById(any());
    }

    @Test
    void shouldThrowWhenRefreshUserNotFound() {
        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(anyString())).thenReturn(1L);
        when(redisService.isRefreshTokenValid(eq(1L), anyString())).thenReturn(true);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.empty());

        assertThrows(AuthException.class, () -> authService.refreshToken("refresh"));
    }

    @Test
    void shouldThrowWhenRefreshAccountInactive() {
        user.setEmailVerified(false);

        when(jwtTokenProvider.validateToken(anyString())).thenReturn(true);
        when(jwtTokenProvider.getUserIdFromToken(anyString())).thenReturn(1L);
        when(redisService.isRefreshTokenValid(eq(1L), anyString())).thenReturn(true);
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(user));

        assertThrows(AuthException.class, () -> authService.refreshToken("refresh"));
    }

    // =====================================================
    // VERIFY EMAIL
    // =====================================================

    @Test
    void shouldVerifyEmailFromRedisCacheSuccessfully() {
        user.setEmailVerified(false);
        user.setVerificationToken("token");
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(1));

        when(userRepository.findActiveByEmail(user.getEmail())).thenReturn(Optional.of(user));
        // Redis cache hit — token returned from cache
        when(redisService.getVerificationToken(user.getEmail())).thenReturn(Optional.of("token"));

        authService.verifyEmail(user.getEmail(), "token");

        assertTrue(user.getEmailVerified());
        verify(userRepository).save(user);
        verify(redisService).evictVerificationToken(user.getEmail());
    }

    @Test
    void shouldVerifyEmailFromDbWhenCacheMisses() {
        user.setEmailVerified(false);
        user.setVerificationToken("token");
        user.setVerificationTokenExpiry(LocalDateTime.now().plusHours(1));

        when(userRepository.findActiveByEmail(user.getEmail())).thenReturn(Optional.of(user));
        // Cache miss
        when(redisService.getVerificationToken(user.getEmail())).thenReturn(Optional.empty());

        authService.verifyEmail(user.getEmail(), "token");

        assertTrue(user.getEmailVerified());
        verify(userRepository).save(user);
        verify(redisService).evictVerificationToken(user.getEmail());
    }

    @Test
    void shouldThrowWhenUserNotFoundForVerification() {
        when(userRepository.findActiveByEmail(anyString())).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> authService.verifyEmail("missing@test.com", "token"));
    }

    @Test
    void shouldThrowWhenAlreadyVerified() {
        user.setEmailVerified(true);
        when(userRepository.findActiveByEmail(anyString())).thenReturn(Optional.of(user));

        assertThrows(EmailVerificationException.class,
                () -> authService.verifyEmail(user.getEmail(), "token"));
    }

    @Test
    void shouldThrowWhenTokenInvalidAndCacheMisses() {
        user.setEmailVerified(false);
        user.setVerificationToken("correct");

        when(userRepository.findActiveByEmail(anyString())).thenReturn(Optional.of(user));
        when(redisService.getVerificationToken(anyString())).thenReturn(Optional.empty());

        assertThrows(EmailVerificationException.class,
                () -> authService.verifyEmail(user.getEmail(), "wrong"));
    }

    @Test
    void shouldThrowWhenTokenExpiredAndCacheMisses() {
        user.setEmailVerified(false);
        user.setVerificationToken("token");
        user.setVerificationTokenExpiry(LocalDateTime.now().minusHours(1));

        when(userRepository.findActiveByEmail(anyString())).thenReturn(Optional.of(user));
        when(redisService.getVerificationToken(anyString())).thenReturn(Optional.empty());

        assertThrows(EmailVerificationException.class,
                () -> authService.verifyEmail(user.getEmail(), "token"));
    }

    @Test
    void shouldAcceptValidTokenEvenIfExpiredInDbWhenCacheHits() {
        // If Redis says the token is valid, DB expiry is not re-checked
        user.setEmailVerified(false);
        user.setVerificationToken("token");
        user.setVerificationTokenExpiry(LocalDateTime.now().minusHours(1)); // expired in DB

        when(userRepository.findActiveByEmail(user.getEmail())).thenReturn(Optional.of(user));
        when(redisService.getVerificationToken(user.getEmail())).thenReturn(Optional.of("token"));

        // Should NOT throw — cache is the authority
        assertDoesNotThrow(() -> authService.verifyEmail(user.getEmail(), "token"));
        assertTrue(user.getEmailVerified());
    }

    // =====================================================
    // RESEND EMAIL
    // =====================================================

    @Test
    void shouldResendVerificationEmailSuccessfully() {
        user.setEmailVerified(false);
        when(userRepository.findByEmailAndIsDeletedFalse(user.getEmail()))
                .thenReturn(Optional.of(user));

        authService.resendVerificationEmail(user.getEmail());

        verify(userRepository).save(user);
        verify(redisService).cacheVerificationToken(eq(user.getEmail()), anyString());
        verify(emailService).sendEmailVerification(eq(user.getEmail()), anyString());
    }

    @Test
    void shouldThrowWhenResendingToVerifiedUser() {
        user.setEmailVerified(true);
        when(userRepository.findByEmailAndIsDeletedFalse(user.getEmail()))
                .thenReturn(Optional.of(user));

        assertThrows(EmailVerificationException.class,
                () -> authService.resendVerificationEmail(user.getEmail()));
    }

    @Test
    void shouldThrowWhenResendUserNotFound() {
        when(userRepository.findByEmailAndIsDeletedFalse("ghost@test.com"))
                .thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class,
                () -> authService.resendVerificationEmail("ghost@test.com"));
    }
}