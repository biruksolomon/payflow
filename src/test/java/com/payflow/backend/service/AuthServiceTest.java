package com.payflow.backend.service;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.dto.AuthRequest;
import com.payflow.backend.dto.AuthResponse;
import com.payflow.backend.dto.RegisterRequest;
import com.payflow.backend.exception.*;
import com.payflow.backend.repository.UserRepository;
import com.payflow.backend.security.JwtTokenProvider;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private AuthenticationManager authenticationManager;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @Mock
    private EmailService emailService;

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

        when(userRepository.existsByEmail(request.getEmail()))
                .thenReturn(false);

        when(passwordEncoder.encode(request.getPassword()))
                .thenReturn("encodedPassword");

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> {
                    User saved = invocation.getArgument(0);
                    saved.setId(1L);
                    return saved;
                });

        when(jwtTokenProvider.generateAccessToken(any(Authentication.class)))
                .thenReturn("access-token");

        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong()))
                .thenReturn("refresh-token");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());
        assertEquals("refresh-token", response.getRefreshToken());

        verify(emailService).sendEmailVerification(
                eq("user@test.com"),
                anyString()
        );
    }

    @Test
    void shouldThrowWhenPasswordsDoNotMatch() {

        RegisterRequest request = new RegisterRequest();
        request.setPassword("123");
        request.setPasswordConfirm("456");

        assertThrows(
                PasswordMismatchException.class,
                () -> authService.register(request)
        );

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenEmailAlreadyExists() {

        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("123");
        request.setPasswordConfirm("123");

        when(userRepository.existsByEmail("user@test.com"))
                .thenReturn(true);

        assertThrows(
                DuplicateEmailException.class,
                () -> authService.register(request)
        );
    }

    @Test
    void shouldContinueRegistrationWhenEmailFails() {

        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("123456");
        request.setPasswordConfirm("123456");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(userRepository.existsByEmail(anyString()))
                .thenReturn(false);

        when(passwordEncoder.encode(anyString()))
                .thenReturn("encoded");

        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        doThrow(new RuntimeException("Mail Error"))
                .when(emailService)
                .sendEmailVerification(anyString(), anyString());

        when(jwtTokenProvider.generateAccessToken(any(Authentication.class)))
                .thenReturn("access");

        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong()))
                .thenReturn("refresh");

        AuthResponse response = authService.register(request);

        assertNotNull(response);
    }

    // =====================================================
    // LOGIN
    // =====================================================

    @Test
    void shouldLoginSuccessfully() {

        AuthRequest request = new AuthRequest();
        request.setEmail("user@test.com");
        request.setPassword("password");

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                );

        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);

        when(userRepository.findActiveByEmail(request.getEmail()))
                .thenReturn(Optional.of(user));

        when(jwtTokenProvider.generateAccessToken(authentication))
                .thenReturn("access-token");

        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong()))
                .thenReturn("refresh-token");

        AuthResponse response = authService.login(request);

        assertNotNull(response);
        assertEquals("access-token", response.getAccessToken());

        verify(userRepository).save(user);
    }

    @Test
    void shouldThrowWhenCredentialsInvalid() {

        AuthRequest request = new AuthRequest();
        request.setEmail("user@test.com");
        request.setPassword("wrong");

        when(authenticationManager.authenticate(any()))
                .thenThrow(new BadCredentialsException("Invalid"));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );
    }

    @Test
    void shouldThrowWhenUserNotFoundAfterAuthentication() {

        AuthRequest request = new AuthRequest();
        request.setEmail("user@test.com");
        request.setPassword("password");

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                );

        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);

        when(userRepository.findActiveByEmail(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(
                RuntimeException.class,
                () -> authService.login(request)
        );
    }

    @Test
    void shouldThrowWhenAccountInactive() {

        user.setAccountStatus(AccountStatus.SUSPENDED);

        AuthRequest request = new AuthRequest();
        request.setEmail(user.getEmail());
        request.setPassword("password");

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                );

        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);

        when(userRepository.findActiveByEmail(anyString()))
                .thenReturn(Optional.of(user));

        assertThrows(
                InvalidCredentialsException.class,
                () -> authService.login(request)
        );
    }

    @Test
    void shouldThrowWhenEmailNotVerified() {

        user.setEmailVerified(false);

        AuthRequest request = new AuthRequest();
        request.setEmail(user.getEmail());
        request.setPassword("password");

        Authentication authentication =
                new UsernamePasswordAuthenticationToken(
                        request.getEmail(),
                        request.getPassword()
                );

        when(authenticationManager.authenticate(any()))
                .thenReturn(authentication);

        when(userRepository.findActiveByEmail(anyString()))
                .thenReturn(Optional.of(user));

        assertThrows(
                AuthException.class,
                () -> authService.login(request)
        );
    }

    // =====================================================
    // VERIFY EMAIL
    // =====================================================

    @Test
    void shouldVerifyEmailSuccessfully() {

        user.setEmailVerified(false);
        user.setVerificationToken("token");
        user.setVerificationTokenExpiry(
                LocalDateTime.now().plusHours(1)
        );

        when(userRepository.findActiveByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        authService.verifyEmail(user.getEmail(), "token");

        assertTrue(user.getEmailVerified());

        verify(userRepository).save(user);
    }

    @Test
    void shouldThrowWhenUserNotFoundForVerification() {

        when(userRepository.findActiveByEmail(anyString()))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> authService.verifyEmail(
                        "missing@test.com",
                        "token"
                )
        );
    }

    @Test
    void shouldThrowWhenAlreadyVerified() {

        user.setEmailVerified(true);

        when(userRepository.findActiveByEmail(anyString()))
                .thenReturn(Optional.of(user));

        assertThrows(
                EmailVerificationException.class,
                () -> authService.verifyEmail(
                        user.getEmail(),
                        "token"
                )
        );
    }

    @Test
    void shouldThrowWhenTokenInvalid() {

        user.setEmailVerified(false);
        user.setVerificationToken("correct");

        when(userRepository.findActiveByEmail(anyString()))
                .thenReturn(Optional.of(user));

        assertThrows(
                EmailVerificationException.class,
                () -> authService.verifyEmail(
                        user.getEmail(),
                        "wrong"
                )
        );
    }

    @Test
    void shouldThrowWhenTokenExpired() {

        user.setEmailVerified(false);
        user.setVerificationToken("token");
        user.setVerificationTokenExpiry(
                LocalDateTime.now().minusHours(1)
        );

        when(userRepository.findActiveByEmail(anyString()))
                .thenReturn(Optional.of(user));

        assertThrows(
                EmailVerificationException.class,
                () -> authService.verifyEmail(
                        user.getEmail(),
                        "token"
                )
        );
    }

    // =====================================================
    // RESEND EMAIL
    // =====================================================

    @Test
    void shouldResendVerificationEmailSuccessfully() {

        user.setEmailVerified(false);

        when(userRepository.findByEmailAndIsDeletedFalse(
                user.getEmail()))
                .thenReturn(Optional.of(user));

        authService.resendVerificationEmail(
                user.getEmail()
        );

        verify(userRepository).save(user);

        verify(emailService)
                .sendEmailVerification(
                        eq(user.getEmail()),
                        anyString()
                );
    }

    @Test
    void shouldThrowWhenResendingToVerifiedUser() {

        user.setEmailVerified(true);

        when(userRepository.findByEmailAndIsDeletedFalse(
                user.getEmail()))
                .thenReturn(Optional.of(user));

        assertThrows(
                EmailVerificationException.class,
                () -> authService.resendVerificationEmail(
                        user.getEmail()
                )
        );
    }

    // =====================================================
    // REFRESH TOKEN
    // =====================================================

    @Test
    void shouldRefreshTokenSuccessfully() {

        when(jwtTokenProvider.validateToken("refresh"))
                .thenReturn(true);

        when(jwtTokenProvider.getUserIdFromToken("refresh"))
                .thenReturn(1L);

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(jwtTokenProvider.generateAccessToken(any(Authentication.class)))
                .thenReturn("new-access");

        when(jwtTokenProvider.generateRefreshToken(anyString(), anyLong()))
                .thenReturn("new-refresh");

        AuthResponse response =
                authService.refreshToken("refresh");

        assertEquals(
                "new-access",
                response.getAccessToken()
        );

        assertEquals(
                "new-refresh",
                response.getRefreshToken()
        );
    }

    @Test
    void shouldThrowWhenRefreshTokenInvalid() {

        when(jwtTokenProvider.validateToken("bad-token"))
                .thenReturn(false);

        assertThrows(
                AuthException.class,
                () -> authService.refreshToken("bad-token")
        );
    }

    @Test
    void shouldThrowWhenRefreshUserNotFound() {

        when(jwtTokenProvider.validateToken(anyString()))
                .thenReturn(true);

        when(jwtTokenProvider.getUserIdFromToken(anyString()))
                .thenReturn(1L);

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                AuthException.class,
                () -> authService.refreshToken("refresh")
        );
    }

    @Test
    void shouldThrowWhenRefreshAccountInactive() {

        user.setEmailVerified(false);

        when(jwtTokenProvider.validateToken(anyString()))
                .thenReturn(true);

        when(jwtTokenProvider.getUserIdFromToken(anyString()))
                .thenReturn(1L);

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        assertThrows(
                AuthException.class,
                () -> authService.refreshToken("refresh")
        );
    }
}