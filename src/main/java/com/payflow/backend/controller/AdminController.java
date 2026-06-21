package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.dto.request.CreateAdminRequest;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.AdminService;
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

import java.util.List;
import java.util.Map;

/**
 * AdminController — endpoints accessible only by SUPER_ADMIN.
 *
 * All routes are prefixed with /api/super-admin so that SecurityConfig can
 * apply a single hasRole("SUPER_ADMIN") rule to the entire path.
 */
@Slf4j
@RestController
@RequestMapping("/api/super-admin")
@RequiredArgsConstructor
@Tag(name = "Super Admin", description = "Super administrator management endpoints")
@SecurityRequirement(name = "Bearer Authentication")
public class AdminController {

    private final AdminService adminService;

    // ─────────────────────────────────────────────────────────────
    // CREATE ADMIN
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/admins")
    @Operation(summary = "Create a new admin account (super admin only)")
    public ResponseEntity<User> createAdmin(
            @Valid @RequestBody CreateAdminRequest request,
            Authentication authentication) {

        resolveSuperAdmin(authentication);
        log.info("Super admin creating new admin: {}", request.getEmail());
        User admin = adminService.createAdmin(request);
        log.info("Admin account created: id={}", admin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(admin);
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE ADMIN (soft)
    // ─────────────────────────────────────────────────────────────

    @DeleteMapping("/admins/{id}")
    @Operation(summary = "Soft-delete an admin account (super admin only)")
    public ResponseEntity<Map<String, String>> deleteAdmin(
            @PathVariable Long id,
            Authentication authentication) {

        resolveSuperAdmin(authentication);
        log.info("Super admin deleting admin id={}", id);
        adminService.deleteAdmin(id);
        return ResponseEntity.ok(Map.of("message", "Admin account deleted successfully"));
    }

    // ─────────────────────────────────────────────────────────────
    // PROMOTE / DEMOTE
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/users/{id}/promote")
    @Operation(summary = "Promote a customer to admin (super admin only)")
    public ResponseEntity<User> promoteToAdmin(
            @PathVariable Long id,
            Authentication authentication) {

        resolveSuperAdmin(authentication);
        log.info("Super admin promoting user id={} to ADMIN", id);
        User promoted = adminService.promoteToAdmin(id);
        return ResponseEntity.ok(promoted);
    }

    @PostMapping("/admins/{id}/demote")
    @Operation(summary = "Demote an admin back to customer (super admin only)")
    public ResponseEntity<User> demoteToCustomer(
            @PathVariable Long id,
            Authentication authentication) {

        resolveSuperAdmin(authentication);
        log.info("Super admin demoting admin id={} to CUSTOMER", id);
        User demoted = adminService.demoteToCustomer(id);
        return ResponseEntity.ok(demoted);
    }

    // ─────────────────────────────────────────────────────────────
    // LIST
    // ─────────────────────────────────────────────────────────────

    @GetMapping("/admins")
    @Operation(summary = "List all admin accounts (super admin only)")
    public ResponseEntity<List<User>> getAllAdmins(Authentication authentication) {
        resolveSuperAdmin(authentication);
        return ResponseEntity.ok(adminService.getAllAdmins());
    }

    @GetMapping("/super-admins")
    @Operation(summary = "List all super admin accounts (super admin only)")
    public ResponseEntity<List<User>> getAllSuperAdmins(Authentication authentication) {
        resolveSuperAdmin(authentication);
        return ResponseEntity.ok(adminService.getAllSuperAdmins());
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────

    /**
     * Resolves and validates that the caller is an authenticated SUPER_ADMIN.
     * This is a defence-in-depth guard: the SecurityFilterChain already restricts
     * /api/super-admin/** to ROLE_SUPER_ADMIN, but we verify here explicitly so
     * that the service layer cannot be called with an unexpected principal.
     */
    private void resolveSuperAdmin(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required", "UNAUTHORIZED");
        }
        PayFlowUserDetails userDetails = (PayFlowUserDetails) authentication.getPrincipal();
        if (!userDetails.isSuperAdmin()) {
            throw new AuthException(
                    "Super admin access required",
                    "FORBIDDEN",
                    HttpStatus.FORBIDDEN);
        }
    }
}
