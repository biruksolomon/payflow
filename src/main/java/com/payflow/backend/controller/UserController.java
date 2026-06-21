package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import org.springframework.http.HttpStatus;
import com.payflow.backend.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
        User user = userService.getProfile(userDetails.getId(), userDetails.getId(), userDetails.isAdmin());
        return ResponseEntity.ok(user);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a user profile by ID (admin or self only)")
    public ResponseEntity<User> getProfileById(
            @PathVariable Long id,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);
        User user = userService.getProfile(id, userDetails.getId(), userDetails.isAdmin());
        return ResponseEntity.ok(user);
    }

    @PutMapping("/me")
    @Operation(summary = "Update the authenticated user's profile")
    public ResponseEntity<User> updateMyProfile(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);

        PaymentMethod preferredPaymentMethod = parseEnum(body.get("preferredPaymentMethod"), PaymentMethod.class);
        Currency preferredCurrency = parseEnum(body.get("preferredCurrency"), Currency.class);

        User updated = userService.updateProfile(
                userDetails.getId(),
                (String) body.get("firstName"),
                (String) body.get("lastName"),
                (String) body.get("phone"),
                (String) body.get("streetAddress"),
                (String) body.get("city"),
                (String) body.get("stateProvince"),
                (String) body.get("postalCode"),
                (String) body.get("country"),
                preferredPaymentMethod,
                preferredCurrency);

        log.info("Profile updated for userId={}", userDetails.getId());
        return ResponseEntity.ok(updated);
    }

    // ─────────────────────────────────────────────────────────────
    // PASSWORD CHANGE
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/me/change-password")
    @Operation(summary = "Change the authenticated user's password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody Map<String, String> body,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);

        String currentPassword   = body.get("currentPassword");
        String newPassword       = body.get("newPassword");
        String confirmNewPassword = body.get("confirmNewPassword");

        if (currentPassword == null || newPassword == null || confirmNewPassword == null) {
            throw new AuthException("currentPassword, newPassword and confirmNewPassword are required", "INVALID_REQUEST", HttpStatus.BAD_REQUEST);
        }

        userService.changePassword(userDetails.getId(), currentPassword, newPassword, confirmNewPassword);
        log.info("Password changed for userId={}", userDetails.getId());
        return ResponseEntity.ok(Map.of("message", "Password changed successfully"));
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
    public ResponseEntity<Map<String, String>> deleteAccount(
            @PathVariable Long id,
            Authentication authentication) {

        PayFlowUserDetails userDetails = resolveUser(authentication);
        userService.softDeleteAccount(id, userDetails.getId(), userDetails.isAdmin());
        log.info("Account deleted — targetUserId={} requestingUserId={}", id, userDetails.getId());
        return ResponseEntity.ok(Map.of("message", "Account deleted successfully"));
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
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private PayFlowUserDetails resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required", "UNAUTHORIZED");
        }
        return (PayFlowUserDetails) authentication.getPrincipal();
    }

    private <T extends Enum<T>> T parseEnum(Object value, Class<T> enumClass) {
        if (value == null) return null;
        try { return Enum.valueOf(enumClass, value.toString().toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
