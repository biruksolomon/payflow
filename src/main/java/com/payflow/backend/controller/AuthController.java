package com.payflow.backend.controller;


import com.payflow.backend.dto.request.AuthRequest;
import com.payflow.backend.dto.request.LogoutRequest;
import com.payflow.backend.dto.request.RefreshTokenRequest;
import com.payflow.backend.dto.request.RegisterRequest;
import com.payflow.backend.dto.response.AuthResponse;
import com.payflow.backend.dto.response.CurrentUserResponse;
import com.payflow.backend.dto.response.MessageResponse;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and authorization endpoints")
public class AuthController {

    private final AuthService authService;

    // ─────────────────────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/register")
    @Operation(summary = "Register new user",
            description = "Create a new user account. Rate-limited to 3 attempts/hour per IP.")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        log.info("Register endpoint called for email: {}", request.getEmail());
        AuthResponse response = authService.register(request, httpRequest);
        log.info("User registration successful");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/login")
    @Operation(summary = "User login",
            description = "Authenticate user. Rate-limited to 5 attempts/15 min per email.")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody AuthRequest request,
            HttpServletRequest httpRequest) {

        log.info("Login endpoint called for email: {}", request.getEmail());
        AuthResponse response = authService.login(request, httpRequest);
        log.info("User login successful");
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────
    // EMAIL VERIFICATION
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address",
            description = "Verify user email with verification token (Redis-cached for speed)")
    public ResponseEntity<MessageResponse> verifyEmail(
            @RequestParam String email,
            @RequestParam String token) {

        log.info("Email verification endpoint called for: {}", email);
        authService.verifyEmail(email, token);
        log.info("Email verified successfully");
        return ResponseEntity.ok(MessageResponse.of("Email verified successfully"));
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email",
            description = "Send a new verification email, updating Redis cache")
    public ResponseEntity<MessageResponse> resendVerification(@RequestParam String email) {
        log.info("Resend verification endpoint called for: {}", email);
        authService.resendVerificationEmail(email);
        log.info("Verification email resent successfully");
        return ResponseEntity.ok(MessageResponse.of("Verification email sent successfully"));
    }

    // ─────────────────────────────────────────────────────────────
    // TOKEN REFRESH
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token",
            description = "Rotate refresh token (old is revoked, new pair issued)")
    public ResponseEntity<AuthResponse> refreshToken(
            @Valid @RequestBody RefreshTokenRequest request) {

        log.info("Refresh token endpoint called");
        AuthResponse response = authService.refreshToken(request.getRefreshToken());
        log.info("Token refreshed successfully");
        return ResponseEntity.ok(response);
    }

    // ─────────────────────────────────────────────────────────────
    // CURRENT USER
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Get current user",
            description = "Get information about the currently authenticated user")
    public ResponseEntity<CurrentUserResponse> getCurrentUser(Authentication authentication) {
        log.info("Get current user endpoint called");
        if (authentication == null || !authentication.isAuthenticated()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PayFlowUserDetails userDetails = (PayFlowUserDetails) authentication.getPrincipal();
        return ResponseEntity.ok(CurrentUserResponse.from(userDetails));
    }

    // ─────────────────────────────────────────────────────────────
    // LOGOUT  (current device)
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/logout")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Logout current device",
            description = "Blacklists the access token and revokes this session's refresh token")
    public ResponseEntity<MessageResponse> logout(
            Authentication authentication,
            HttpServletRequest httpRequest,
            @RequestBody(required = false) LogoutRequest body) {

        log.info("Logout endpoint called");
        if (authentication != null && authentication.isAuthenticated()) {
            PayFlowUserDetails userDetails = (PayFlowUserDetails) authentication.getPrincipal();
            String accessToken  = extractBearerToken(httpRequest);
            String refreshToken = body != null ? body.getRefreshToken() : null;
            authService.logout(accessToken, refreshToken, userDetails.getId());
            log.info("User {} logged out", userDetails.getId());
        }
        return ResponseEntity.ok(MessageResponse.of("Logout successful"));
    }

    // ─────────────────────────────────────────────────────────────
    // LOGOUT ALL  (every device)
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/logout-all")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Logout all devices",
            description = "Blacklists the current access token and revokes ALL refresh tokens for this user")
    public ResponseEntity<MessageResponse> logoutAll(
            Authentication authentication,
            HttpServletRequest httpRequest) {

        log.info("Logout-all endpoint called");
        if (authentication != null && authentication.isAuthenticated()) {
            PayFlowUserDetails userDetails = (PayFlowUserDetails) authentication.getPrincipal();
            String accessToken = extractBearerToken(httpRequest);
            authService.logoutAll(accessToken, userDetails.getId());
            log.info("User {} logged out from all devices", userDetails.getId());
        }
        return ResponseEntity.ok(MessageResponse.of("Logged out from all devices"));
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────

    private String extractBearerToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            return header.substring(7);
        }
        return null;
    }
}
