package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.dto.request.ChangePasswordRequest;
import com.payflow.backend.dto.request.UpdateProfileRequest;
import com.payflow.backend.dto.response.MessageResponse;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Tag(name = "Users", description = "User profile and account management")
@SecurityRequirement(name = "Bearer Authentication")
public class UserController {

    private final UserService userService;

    // ─────────────────────────────────────────────────────────────
    // PROFILE
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/me")
    @Operation(summary = "Get the authenticated user's profile")
    public ResponseEntity<User> getMyProfile(Authentication authentication) {
        PayFlowUserDetails userDetails = resolveUser(authentication);
        User user = userService.getProfile(userDetails.getId(), userDetails.getId(), userDetails.hasAdminPrivileges());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user profile by ID (admin or self only)")
    public ResponseEntity<User> getProfileById(
            @PathVariable Long id,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);
        User user = userService.getProfile(id, userDetails.getId(), userDetails.hasAdminPrivileges());
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    @Operation(summary = "Update the authenticated user's profile")
    public ResponseEntity<User> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);

        User updated = userService.updateProfile(
                userDetails.getId(),
                request.getFirstName(),
                request.getLastName(),
                request.getPhone(),
                request.getStreetAddress(),
                request.getCity(),
                request.getStateProvince(),
                request.getPostalCode(),
                request.getCountry(),
                request.getPreferredPaymentMethod(),
                request.getPreferredCurrency());

        log.info("Profile updated for userId={}", userDetails.getId());
        return ResponseEntity.ok(updated);
    }

    // ─────────────────────────────────────────────────────────────
    // PASSWORD CHANGE
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/me/change-password")
    @Operation(summary = "Change the authenticated user's password")
    public ResponseEntity<MessageResponse> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);
        userService.changePassword(
                userDetails.getId(),
                request.getCurrentPassword(),
                request.getNewPassword(),
                request.getConfirmNewPassword());

        log.info("Password changed for userId={}", userDetails.getId());
        return ResponseEntity.ok(MessageResponse.of("Password changed successfully"));
    }

    // ─────────────────────────────────────────────────────────────
    // ADMIN — account status management
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/{id}/suspend")
    @Operation(summary = "Suspend a user account (admin only)")
    public ResponseEntity<User> suspendAccount(@PathVariable Long id) {
        User user = userService.suspendAccount(id);
        log.info("Account suspended — userId={}", id);
        return ResponseEntity.ok(user);
    }

    @PostMapping("/{id}/reactivate")
    @Operation(summary = "Reactivate a suspended user account (admin only)")
    public ResponseEntity<User> reactivateAccount(@PathVariable Long id) {
        User user = userService.reactivateAccount(id);
        log.info("Account reactivated — userId={}", id);
        return ResponseEntity.ok(user);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Soft-delete a user account (admin or self only)")
    public ResponseEntity<MessageResponse> deleteAccount(
            @PathVariable Long id,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);
        userService.softDeleteAccount(id, userDetails.getId(), userDetails.hasAdminPrivileges());
        log.info("Account deleted — targetUserId={} requestingUserId={}", id, userDetails.getId());
        return ResponseEntity.ok(MessageResponse.of("Account deleted successfully"));
    }

    // ─────────────────────────────────────────────────────────────
    // ADMIN — lists
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/admin/customers")
    @Operation(summary = "List all customer accounts (admin only)")
    public ResponseEntity<List<User>> getAllCustomers() {
        return ResponseEntity.ok(userService.getAllUsers());
    }

    @GetMapping("/admin/admins")
    @Operation(summary = "List all admin accounts (admin only)")
    public ResponseEntity<List<User>> getAllAdmins() {
        return ResponseEntity.ok(userService.getAllAdmins());
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────

    private PayFlowUserDetails resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required", "UNAUTHORIZED");
        }
        return (PayFlowUserDetails) authentication.getPrincipal();
    }
}
