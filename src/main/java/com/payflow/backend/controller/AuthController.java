package com.payflow.backend.controller;


import com.payflow.backend.dto.AuthRequest;
import com.payflow.backend.dto.AuthResponse;
import com.payflow.backend.dto.RegisterRequest;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "User authentication and authorization endpoints")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @Operation(summary = "Register new user", description = "Create a new user account with email and password")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        log.info(" Register endpoint called for email: {}", request.getEmail());
        AuthResponse response = authService.register(request);
        log.info(" User registration successful");
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    @Operation(summary = "User login", description = "Authenticate user with email and password")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request) {
        log.info("Login endpoint called for email: {}", request.getEmail());
        AuthResponse response = authService.login(request);
        log.info(" User login successful");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/verify-email")
    @Operation(summary = "Verify email address", description = "Verify user email with verification token")
    public ResponseEntity<Map<String, String>> verifyEmail(
            @RequestParam String email,
            @RequestParam String token) {
        log.info("Email verification endpoint called for: {}", email);
        authService.verifyEmail(email, token);
        log.info(" Email verified successfully");
        Map<String, String> response = new HashMap<>();
        response.put("message", "Email verified successfully");
        response.put("email", email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/resend-verification")
    @Operation(summary = "Resend verification email", description = "Send a new verification email to the user")
    public ResponseEntity<Map<String, String>> resendVerification(@RequestParam String email) {
        log.info(" Resend verification endpoint called for: {}", email);
        authService.resendVerificationEmail(email);
        log.info("Verification email resent successfully");
        Map<String, String> response = new HashMap<>();
        response.put("message", "Verification email sent successfully");
        response.put("email", email);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh-token")
    @Operation(summary = "Refresh access token", description = "Get a new access token using refresh token")
    public ResponseEntity<AuthResponse> refreshToken(@RequestBody Map<String, String> request) {
        log.info(" Refresh token endpoint called");
        String refreshToken = request.get("refresh_token");
        if (refreshToken == null || refreshToken.isEmpty()) {
            log.warn(" Refresh token not provided");
            return ResponseEntity.badRequest().build();
        }
        AuthResponse response = authService.refreshToken(refreshToken);
        log.info("Token refreshed successfully");
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Get current user", description = "Get information about the currently authenticated user")
    public ResponseEntity<Map<String, Object>> getCurrentUser(Authentication authentication) {
        log.info(" Get current user endpoint called");
        if (authentication == null || !authentication.isAuthenticated()) {
            log.warn(" Unauthorized access to /me endpoint");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        PayFlowUserDetails userDetails = (PayFlowUserDetails) authentication.getPrincipal();
        Map<String, Object> response = new HashMap<>();
        response.put("id", userDetails.getId());
        response.put("email", userDetails.getUsername());
        response.put("firstName", userDetails.getFirstName());
        response.put("lastName", userDetails.getLastName());
        response.put("fullName", userDetails.getFirstName() + userDetails.getLastName());
        response.put("role", userDetails.getAuthorities());
        response.put("emailVerified", userDetails.isEmailVerified());
        response.put("accountActive", userDetails.isEnabled());
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    @SecurityRequirement(name = "bearer")
    @Operation(summary = "Logout user", description = "Logout the current user (client removes token)")
    public ResponseEntity<Map<String, String>> logout(Authentication authentication) {
        log.info(" Logout endpoint called");
        if (authentication != null && authentication.isAuthenticated()) {
            PayFlowUserDetails userDetails = (PayFlowUserDetails) authentication.getPrincipal();
            log.info("User logged out: {}", userDetails.getId());
        }
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logout successful");
        return ResponseEntity.ok(response);
    }
}