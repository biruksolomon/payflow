package com.payflow.backend.service;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.dto.request.CreateAdminRequest;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.DuplicateEmailException;
import com.payflow.backend.exception.PasswordMismatchException;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * AdminService — operations available exclusively to the SUPER_ADMIN role.
 *
 * Responsibilities:
 *  - createAdmin      : provision a new ADMIN account
 *  - deleteAdmin      : soft-delete an ADMIN account (SUPER_ADMIN cannot be deleted here)
 *  - promoteToAdmin   : elevate a CUSTOMER to ADMIN
 *  - demoteToCustomer : downgrade an ADMIN back to CUSTOMER
 *  - getAllAdmins      : list every non-deleted ADMIN
 *  - getAllSuperAdmins : list every non-deleted SUPER_ADMIN
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class AdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ─────────────────────────────────────────────────────────────
    // CREATE ADMIN
    // ─────────────────────────────────────────────────────────────

    /**
     * Creates a new ADMIN account.
     * The calling user MUST be a SUPER_ADMIN (enforced at the controller/security layer).
     *
     * @param request  the new admin's credentials and personal details
     * @return         the persisted User entity
     */
    @Transactional
    public User createAdmin(CreateAdminRequest request) {
        log.info("Creating new admin account: {}", request.getEmail());

        if (!request.getPassword().equals(request.getPasswordConfirm())) {
            throw new PasswordMismatchException();
        }

        if (userRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateEmailException(request.getEmail());
        }

        User admin = User.builder()
                .email(request.getEmail())
                .passwordHash(passwordEncoder.encode(request.getPassword()))
                .firstName(request.getFirstName())
                .lastName(request.getLastName())
                .userRole(UserRole.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)          // admin accounts are pre-verified
                .isDeleted(false)
                .build();

        User saved = userRepository.save(admin);
        log.info("Admin account created with id={}", saved.getId());
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE ADMIN (soft)
    // ─────────────────────────────────────────────────────────────

    /**
     * Soft-deletes an ADMIN account.
     * SUPER_ADMIN accounts cannot be deleted via this operation.
     *
     * @param targetUserId  id of the ADMIN to delete
     */
    @Transactional
    public void deleteAdmin(Long targetUserId) {
        User user = userRepository.findActiveById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (user.isSuperAdmin()) {
            throw new AuthException(
                    "Super admin accounts cannot be deleted via this endpoint",
                    "FORBIDDEN",
                    HttpStatus.FORBIDDEN);
        }

        if (!user.isAdmin()) {
            throw new AuthException(
                    "Target user is not an admin",
                    "NOT_AN_ADMIN",
                    HttpStatus.BAD_REQUEST);
        }

        user.setIsDeleted(true);
        user.setDeletedAt(java.time.LocalDateTime.now());
        user.setAccountStatus(AccountStatus.DELETED);
        userRepository.save(user);
        log.info("Admin account soft-deleted: id={}", targetUserId);
    }

    // ─────────────────────────────────────────────────────────────
    // PROMOTE / DEMOTE
    // ─────────────────────────────────────────────────────────────

    /**
     * Promotes a CUSTOMER account to ADMIN.
     */
    @Transactional
    public User promoteToAdmin(Long targetUserId) {
        User user = userRepository.findActiveById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (user.isSuperAdmin()) {
            throw new AuthException(
                    "Super admin accounts cannot be modified via this endpoint",
                    "FORBIDDEN",
                    HttpStatus.FORBIDDEN);
        }

        if (user.isAdmin()) {
            throw new AuthException(
                    "User is already an admin",
                    "ALREADY_ADMIN",
                    HttpStatus.BAD_REQUEST);
        }

        user.setUserRole(UserRole.ADMIN);
        User saved = userRepository.save(user);
        log.info("User id={} promoted to ADMIN", targetUserId);
        return saved;
    }

    /**
     * Demotes an ADMIN back to CUSTOMER.
     * SUPER_ADMIN accounts are protected from demotion.
     */
    @Transactional
    public User demoteToCustomer(Long targetUserId) {
        User user = userRepository.findActiveById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (user.isSuperAdmin()) {
            throw new AuthException(
                    "Super admin accounts cannot be demoted via this endpoint",
                    "FORBIDDEN",
                    HttpStatus.FORBIDDEN);
        }

        if (!user.isAdmin()) {
            throw new AuthException(
                    "User is not an admin",
                    "NOT_AN_ADMIN",
                    HttpStatus.BAD_REQUEST);
        }

        user.setUserRole(UserRole.CUSTOMER);
        User saved = userRepository.save(user);
        log.info("User id={} demoted to CUSTOMER", targetUserId);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // LIST
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<User> getAllAdmins() {
        return userRepository.findByUserRoleAndIsDeletedFalse(UserRole.ADMIN);
    }

    @Transactional(readOnly = true)
    public List<User> getAllSuperAdmins() {
        return userRepository.findByUserRoleAndIsDeletedFalse(UserRole.SUPER_ADMIN);
    }
}
