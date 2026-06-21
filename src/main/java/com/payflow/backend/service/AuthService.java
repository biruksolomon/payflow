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
import com.payflow.backend.security.PayFlowUserDetails;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuthService — handles all authentication flows.
 *
 * Redis integration points (via RedisService):
 *  ① login          → rate-limit check, reset on success
 *  ② register       → rate-limit check, cache verification token
 *  ③ logout         → blacklist access token, revoke refresh token
 *  ④ logoutAll      → blacklist access token, revoke ALL refresh tokens
 *  ⑤ refreshToken   → validate refresh token from Redis, rotate (revoke old, store new)
 *  ⑥ verifyEmail    → validate against Redis cache first, evict after success
 *  ⑦ resendEmail    → refresh cached token
 */
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
    private final RedisService redisService;          // ← injected

    @Value("${app.jwt.expiration}")
    private long accessTokenExpirationMs;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpirationMs;

    // ─────────────────────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────────────────────

    /**
     * ① Rate-limit registration per IP (3 attempts / hour).
     * ② Hash & persist new user.
     * ③ Cache verification token in Redis (24 h TTL).
     * ④ Issue access + refresh tokens; store refresh token in Redis.
     */
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        log.info("Registering new user with email: {}", request.getEmail());

        // ① Rate-limit
        String ip = extractClientIp(httpRequest);
        if (redisService.isRegisterRateLimited(ip)) {
            log.warn("Registration rate limit exceeded for IP: {}", ip);
            throw new AuthException("Too many registration attempts. Please try again later.", "RATE_LIMITED");
        }
        redisService.incrementRegisterAttempts(ip);

        // Validate passwords match
        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            log.warn("Password mismatch for registration attempt: {}", request.getEmail());
            throw new PasswordMismatchException();
        }

        // Check if email already exists
        if (userRepository.existsByEmail(request.getEmail())) {
            log.warn("Duplicate email registration attempt: {}", request.getEmail());
            throw new DuplicateEmailException(request.getEmail());
        }

        try {
            String encodedPassword    = passwordEncoder.encode(request.getPassword());
            String verificationToken  = UUID.randomUUID().toString();
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

            // ③ Cache verification token
            redisService.cacheVerificationToken(request.getEmail(), verificationToken);

            // Send verification email
            try {
                emailService.sendEmailVerification(request.getEmail(), verificationToken);
                log.info("Verification email sent to: {}", request.getEmail());
            } catch (Exception e) {
                log.error("Failed to send verification email to {}: {}", request.getEmail(), e.getMessage());
            }

            // ④ Issue tokens.
            // Build a PayFlowUserDetails from the saved user so that
            // generateAccessToken(Authentication) can cast the principal correctly.
            PayFlowUserDetails userDetails = new PayFlowUserDetails(savedUser);
            Authentication auth = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());
            String accessToken  = jwtTokenProvider.generateAccessToken(auth);
            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    savedUser.getId().toString(), savedUser.getId());

            storeRefreshToken(savedUser.getId(), refreshToken);

            return buildAuthResponse(accessToken, refreshToken, savedUser);

        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during registration: {}", e.getMessage(), e);
            throw new RuntimeException("Registration failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────

    /**
     * ① Rate-limit by email (5 attempts / 15 min).
     * ② Authenticate.
     * ③ Reset counter on success.
     * ④ Store new refresh token in Redis.
     */
    public AuthResponse login(AuthRequest request, HttpServletRequest httpRequest) {
        log.info("Login attempt for email: {}", request.getEmail());

        // ① Rate-limit
        String rateLimitKey = request.getEmail();
        if (redisService.isLoginRateLimited(rateLimitKey)) {
            long retryAfter = redisService.getLoginRateLimitTtlSeconds(rateLimitKey);
            log.warn("Login rate limit exceeded for: {}", request.getEmail());
            throw new AuthException(
                    "Too many login attempts. Retry after " + retryAfter + "s.",
                    "RATE_LIMITED");
        }

        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(), request.getPassword()));

            User user = userRepository.findActiveByEmail(request.getEmail())
                    .orElseThrow(InvalidCredentialsException::new);

            if (!user.isActive()) {
                log.warn("Login attempt for inactive account: {}", request.getEmail());
                throw new InvalidCredentialsException("Account is not active");
            }
            if (!user.getEmailVerified()) {
                log.warn("Login attempt with unverified email: {}", request.getEmail());
                throw new AuthException(
                        "Please verify your email before logging in", "EMAIL_NOT_VERIFIED");
            }

            // ③ Reset counter
            redisService.resetLoginAttempts(rateLimitKey);

            user.setLastLogin(LocalDateTime.now());
            userRepository.save(user);
            log.info("User logged in successfully with ID: {}", user.getId());

            // ④ Issue & store tokens
            String accessToken  = jwtTokenProvider.generateAccessToken(authentication);
            String refreshToken = jwtTokenProvider.generateRefreshToken(
                    user.getId().toString(), user.getId());

            storeRefreshToken(user.getId(), refreshToken);

            return buildAuthResponse(accessToken, refreshToken, user);

        } catch (BadCredentialsException e) {
            redisService.incrementLoginAttempts(rateLimitKey);   // count bad attempt
            log.warn("Invalid credentials for email: {}", request.getEmail());
            throw new InvalidCredentialsException();
        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error during login: {}", e.getMessage(), e);
            throw new RuntimeException("Login failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // LOGOUT  (single device)
    // ─────────────────────────────────────────────────────────────

    /**
     * Blacklists the current access token and revokes the supplied refresh token.
     *
     * @param accessToken  raw JWT from the Authorization header
     * @param refreshToken refresh token belonging to this session
     * @param userId       authenticated user's ID
     */
    public void logout(String accessToken, String refreshToken, Long userId) {
        log.info("Logout for userId={}", userId);

        // Blacklist access token for its remaining lifetime
        long remainingMs = jwtTokenProvider.getRemainingExpirationMs(accessToken);
        redisService.blacklistToken(accessToken, remainingMs);

        // Revoke the specific refresh token
        if (refreshToken != null && !refreshToken.isBlank()) {
            String hash = md5Hex(refreshToken);
            redisService.revokeRefreshToken(userId, hash);
        }

        log.info("Logout complete for userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────────
    // LOGOUT ALL  (every device)
    // ─────────────────────────────────────────────────────────────

    /**
     * Blacklists the current access token and revokes ALL refresh tokens for the user.
     */
    public void logoutAll(String accessToken, Long userId) {
        log.info("Logout-all for userId={}", userId);

        long remainingMs = jwtTokenProvider.getRemainingExpirationMs(accessToken);
        redisService.blacklistToken(accessToken, remainingMs);
        redisService.revokeAllRefreshTokens(userId);

        log.info("Logout-all complete for userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────────
    // REFRESH TOKEN
    // ─────────────────────────────────────────────────────────────

    /**
     * Validates the refresh token against Redis (server-side store),
     * then rotates: old token is revoked, a new pair is issued.
     */
    public AuthResponse refreshToken(String refreshToken) {
        log.info("Refreshing access token");

        try {
            if (!jwtTokenProvider.validateToken(refreshToken)) {
                log.warn("Invalid refresh token signature/format");
                throw new AuthException("Invalid refresh token", "INVALID_TOKEN");
            }

            Long userId = jwtTokenProvider.getUserIdFromToken(refreshToken);

            // ← Server-side validation: token must exist in Redis
            String hash = md5Hex(refreshToken);
            if (!redisService.isRefreshTokenValid(userId, hash)) {
                log.warn("Refresh token not found in Redis for userId={} (rotated or revoked)", userId);
                throw new AuthException("Refresh token has been revoked", "TOKEN_REVOKED");
            }

            User user = userRepository.findActiveById(userId)
                    .orElseThrow(() -> new UserNotFoundException(userId));

            if (!user.isActive() || !user.getEmailVerified()) {
                log.warn("Token refresh for inactive/unverified account: {}", userId);
                throw new AuthException(
                        "Account is not active or email not verified", "ACCOUNT_INACTIVE");
            }

            // Rotate: revoke old, issue new
            redisService.revokeRefreshToken(userId, hash);

            Authentication auth = new UsernamePasswordAuthenticationToken(
                    user.getEmail(), null, null);
            String newAccessToken  = jwtTokenProvider.generateAccessToken(auth);
            String newRefreshToken = jwtTokenProvider.generateRefreshToken(
                    userId.toString(), userId);

            storeRefreshToken(userId, newRefreshToken);

            log.info("Token refreshed (rotated) for userId={}", userId);
            return buildAuthResponse(newAccessToken, newRefreshToken, user);

        } catch (AuthException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error refreshing token: {}", e.getMessage(), e);
            throw new AuthException("Token refresh failed", "TOKEN_REFRESH_FAILED");
        }
    }

    // ─────────────────────────────────────────────────────────────
    // VERIFY EMAIL
    // ─────────────────────────────────────────────────────────────

    /**
     * Fast path: check Redis cache first, fall back to DB if cache miss.
     * Evicts token from Redis on success.
     */
    public void verifyEmail(String email, String token) {
        log.info("Email verification attempt for: {}", email);

        User user = userRepository.findActiveByEmail(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        if (user.getEmailVerified()) {
            log.warn("Email already verified: {}", email);
            throw EmailVerificationException.alreadyVerified();
        }

        // Check Redis cache first (fast path)
        boolean validFromCache = redisService.getVerificationToken(email)
                .map(cached -> cached.equals(token))
                .orElse(false);

        // Fall back to DB if cache missed
        if (!validFromCache) {
            if (!token.equals(user.getVerificationToken())) {
                log.warn("Invalid verification token for email: {}", email);
                throw EmailVerificationException.invalidToken();
            }
            if (user.getVerificationTokenExpiry() != null &&
                    user.getVerificationTokenExpiry().isBefore(LocalDateTime.now())) {
                log.warn("Verification token expired for email: {}", email);
                throw EmailVerificationException.tokenExpired();
            }
        }

        try {
            user.setEmailVerified(true);
            user.setVerificationToken(null);
            user.setVerificationTokenExpiry(null);
            userRepository.save(user);

            // Evict from Redis
            redisService.evictVerificationToken(email);

            log.info("Email verified successfully for: {}", email);
        } catch (Exception e) {
            log.error("Error verifying email: {}", e.getMessage(), e);
            throw new RuntimeException("Email verification failed", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // RESEND VERIFICATION EMAIL
    // ─────────────────────────────────────────────────────────────

    public void resendVerificationEmail(String email) {
        log.info("Resend verification email for: {}", email);

        User user = userRepository.findByEmailAndIsDeletedFalse(email)
                .orElseThrow(() -> new UserNotFoundException(email));

        if (user.getEmailVerified()) {
            log.warn("Resend verification for already-verified email: {}", email);
            throw EmailVerificationException.alreadyVerified();
        }

        try {
            String newToken           = UUID.randomUUID().toString();
            LocalDateTime newExpiry   = LocalDateTime.now().plusHours(24);

            user.setVerificationToken(newToken);
            user.setVerificationTokenExpiry(newExpiry);
            userRepository.save(user);

            // Refresh Redis cache
            redisService.cacheVerificationToken(email, newToken);

            emailService.sendEmailVerification(email, newToken);
            log.info("Verification email resent to: {}", email);
        } catch (Exception e) {
            log.error("Error resending verification email: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to resend verification email", e);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    /** Store hashed refresh token in Redis with proper TTL. */
    private void storeRefreshToken(Long userId, String rawRefreshToken) {
        String hash = md5Hex(rawRefreshToken);
        redisService.storeRefreshToken(userId, hash, refreshTokenExpirationMs);
    }

    /** MD5 of the raw token — cheap, not used for security, just as a Redis key fragment. */
    private static String md5Hex(String input) {
        return DigestUtils.md5DigestAsHex(input.getBytes());
    }

    /** Best-effort extraction of real client IP (handles proxies/load balancers). */
    private static String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    /** Build the standard AuthResponse DTO. */
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
                .expiresIn(accessTokenExpirationMs / 1000)
                .user(userData)
                .build();
    }
}
