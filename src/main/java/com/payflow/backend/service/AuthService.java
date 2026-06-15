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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtTokenProvider jwtTokenProvider;
    private final EmailService emailService;

    /**
     * Register a new user with email and password
     */
    public AuthResponse register(RegisterRequest request) {
        log.info(" Registering new user with email: {}", request.getEmail());

        // Validate passwords match
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            log.warn(" Password mismatch for registration attempt: {}", request.getEmail());
            throw new PasswordMismatchException();
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn(" Duplicate email registration attempt: {}", request.getEmail());
            throw new DuplicateEmailException(request.getEmail());
        }

        try {
            // Create new user
            String encodedPassword = passwordEncoder.encode(request.getPassword());
            String verificationToken = UUID.randomUUID().toString();
            LocalDateTime tokenExpiry = LocalDateTime.now().plusHours(24);

            User user = User.builder()
                    .email(request.getEmail())
                    .firstName(request.getFirstName())
                    .lastName(request.getLastName())
                    .passwordHash(encodedPassword)
                    .userRole(UserRole.CUSTOMER)
                    .accountStatus(AccountStatus.ACTIVE)
                    .emailVerified(false)
                    .verificationToken(verificationToken)
                    .verificationTokenExpiry(tokenExpiry)
                    .isDeleted(false)
                    .build();

            User savedUser = userRepository.save(user);
            log.info("User registered successfully with ID: {}", savedUser.getId());

            // Send verification email
            try {
                emailService.sendEmailVerification(request.getEmail(), verificationToken);
                log.info("Verification email sent to: {}", request.getEmail());
            } catch (Exception e) {
                log.error("Failed to send verification email to {}: {}", request.getEmail(), e.getMessage());
                // Continue registration even if email fails, user can resend
            }

            // Generate tokens for immediate use
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    savedUser.getEmail(),
                    null,
                    null
            );
            String accessToken = jwtTokenProvider.generateAccessToken(auth);
            String refreshToken = jwtTokenProvider.generateRefreshToken(savedUser.getId().toString(), savedUser.getId());

            return buildAuthResponse(accessToken, refreshToken, savedUser);

        } catch (PasswordMismatchException | DuplicateEmailException e) {
            throw e;
        } catch (Exception e) {
            log.error(" Error during registration: {}", e.getMessage(), e);
            throw new RuntimeException("Registration failed", e);
        }
    }

    /**
     * Authenticate user with email and password
     */
    public AuthResponse login(AuthRequest request) {
        log.info(" Login attempt for email: {}", request.getEmail());

        try {
            // Authenticate with AuthenticationManager
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );

            // Get user from database
            User user = userRepository.findActiveByEmail(request.getEmail())
                    .orElseThrow(() -> new InvalidCredentialsException());

            // Check if account is active
            if (!user.isActive()) {
                log.warn(" Login attempt for inactive account: {}", request.getEmail());
                throw new InvalidCredentialsException("Account is not active");
            }

            // Check if email is verified
            if (!user.getEmailVerified()) {
                log.warn(" Login attempt with unverified email: {}", request.getEmail());
                throw new AuthException("Please verify your email before logging in", "EMAIL_NOT_VERIFIED");
            }

            // Update last login
            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
            log.info("User logged in successfully with ID: {}", user.getId());

            // Generate tokens
            String accessToken = jwtTokenProvider.generateAccessToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(user.getId().toString(), user.getId());

            return buildAuthResponse(accessToken, refreshToken, user);

        } catch (BadCredentialsException e) {
            log.warn("Invalid credentials for email: {}", request.getEmail());
            throw new InvalidCredentialsException();
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error(" Error during login: {}", e.getMessage(), e);
            throw new RuntimeException("Login failed", e);
        }
    }

    /**
     * Verify user email with token
     */
    public void verifyEmail(String email, String token) {
        log.info("Email verification attempt for: {}", email);

        User user = userRepository.findActiveByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        // Check if already verified
        if (user.getEmailVerified()) {
            log.warn(" Email verification attempted for already verified email: {}", email);
            throw EmailVerificationException.alreadyVerified();
        }

        // Check if token matches
        if (!token.equals(user.getVerificationToken())) {
            log.warn(" Invalid verification token for email: {}", email);
            throw EmailVerificationException.invalidToken();
        }

        // Check if token is expired
        if (user.getVerificationTokenExpiry() != null &&
                user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
            log.warn("[v0] Verification token expired for email: {}", email);
            throw EmailVerificationException.tokenExpired();
        }

        try {
            // Mark email as verified
            user.setEmailVerified(true);
            user.setVerificationToken(null);
            user.setVerificationTokenExpiry(null);
            userRepository.save(user);
            log.info("Email verified successfully for: {}", email);

        } catch (Exception e) {
            log.error(" Error verifying email: {}", e.getMessage(), e);
            throw new RuntimeException("Email verification failed", e);
        }
    }

    /**
     * Resend verification email to user
     */
    public void resendVerificationEmail(String email) {
        log.info(" Resend verification email for: {}", email);

        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        // Check if already verified
        if (user.getEmailVerified()) {
            log.warn(" Resend verification for already verified email: {}", email);
            throw EmailVerificationException.alreadyVerified();
        }

        try {
            // Generate new token
            String newToken = UUID.randomUUID().toString();
            LocalDateTime newExpiry = LocalDateTime.now().plusHours(24);

            user.setVerificationToken(newToken);
            user.setVerificationTokenExpiry(newExpiry);
            userRepository.save(user);

            // Send email
            emailService.sendEmailVerification(email, newToken);
            log.info("Verification email resent to: {}", email);

        } catch (Exception e) {
            log.error(" Error resending verification email: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to resend verification email", e);
        }
    }

    /**
     * Refresh access token using refresh token
     */
    public AuthResponse refreshToken(String refreshToken) {
        log.info(" Refreshing access token");

        try {
            // Validate refresh token
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                log.warn(" Invalid refresh token");
                throw new AuthException("Invalid refresh token", "INVALID_TOKEN");
            }

            // Extract user ID from token
            Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);
            User user = userRepository.findActiveById(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));

            // Check if account is active and email verified
            if (!user.isActive() || !user.getEmailVerified()) {
                log.warn("Token refresh for inactive/unverified account: {}", userId);
                throw new AuthException("Account is not active or email not verified", "ACCOUNT_INACTIVE");
            }

            // Generate new tokens
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    user.getEmail(),
                    null,
                    null
            );
            String newAccessToken = jwtTokenProvider.generateAccessToken(auth);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(userId.toString(), userId);

            log.info("Token refreshed successfully for user ID: {}", userId);
            return buildAuthResponse(newAccessToken, newRefreshToken, user);

        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error refreshing token: {}", e.getMessage(), e);
            throw new AuthException("Token refresh failed", "TOKEN_REFRESH_FAILED");
        }
    }

    /**
     * Build AuthResponse from user and tokens
     */
    private AuthResponse buildAuthResponse(String accessToken, String refreshToken, User user) {
        AuthResponse.UserAuthData userData = AuthResponse.UserAuthData.builder()
                .id(user.getId())
                .email(user.getEmail())
                .firstName(user.getFirstName())
                .lastName(user.getLastName())
                .fullName(user.getFullName())
                .role(user.getUserRole().name())
                .emailVerified(user.getEmailVerified())
                .build();

        return AuthResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .tokenType("Bearer")
                .expiresIn(86400L) // 24 hours in seconds
                .user(userData)
                .build();
    }
}
