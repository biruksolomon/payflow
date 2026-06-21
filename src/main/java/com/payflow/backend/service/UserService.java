package com.payflow.backend.service;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * UserService — account management operations for both customers and admins.
 *
 * Operations:
 *  - getProfile          — fetch any active user (customers see only themselves)
 *  - updateProfile       — name, phone, address, preferred payment / currency
 *  - changePassword      — requires current password verification
 *  - suspendAccount      — admin only; sets AccountStatus = SUSPENDED
 *  - reactivateAccount   — admin only; sets AccountStatus = ACTIVE
 *  - softDeleteAccount   — marks isDeleted = true; irreversible from the API
 *  - getAllUsers          — admin only; full list
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    // ─────────────────────────────────────────────────────────────
    // PROFILE
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public User getProfile(Long targetUserId, Long requestingUserId, boolean isAdmin) {
        if (!isAdmin && !targetUserId.equals(requestingUserId)) {
            throw new AuthException("Not authorized to view this profile", "FORBIDDEN");
        }
        return userRepository.findActiveById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));
    }

    @Transactional
    public User updateProfile(
            Long userId,
            String firstName,
            String lastName,
            String phone,
            String streetAddress,
            String city,
            String stateProvince,
            String postalCode,
            String country,
            PaymentMethod preferredPaymentMethod,
            Currency preferredCurrency) {

        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (firstName != null && !firstName.isBlank())       user.setFirstName(firstName);
        if (lastName != null && !lastName.isBlank())         user.setLastName(lastName);
        if (phone != null)                                   user.setPhone(phone);
        if (streetAddress != null)                           user.setStreetAddress(streetAddress);
        if (city != null)                                    user.setCity(city);
        if (stateProvince != null)                           user.setStateProvince(stateProvince);
        if (postalCode != null)                              user.setPostalCode(postalCode);
        if (country != null)                                 user.setCountry(country);
        if (preferredPaymentMethod != null)                  user.setPreferredPaymentMethod(preferredPaymentMethod);
        if (preferredCurrency != null)                       user.setPreferredCurrency(preferredCurrency);

        User saved = userRepository.save(user);
        log.info("Profile updated for userId={}", userId);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // PASSWORD
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void changePassword(Long userId, String currentPassword, String newPassword, String confirmNewPassword) {
        if (!newPassword.equals(confirmNewPassword)) {
            throw new AuthException("New passwords do not match", "PASSWORD_MISMATCH");
        }
        if (newPassword.length() < 8) {
            throw new AuthException("New password must be at least 8 characters", "WEAK_PASSWORD");
        }

        User user = userRepository.findActiveById(userId)
                .orElseThrow(() -> new UserNotFoundException(userId));

        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new AuthException("Current password is incorrect", "INVALID_CURRENT_PASSWORD");
        }

        user.setPasswordHash(passwordEncoder.encode(newPassword));
        userRepository.save(user);
        log.info("Password changed for userId={}", userId);
    }

    // ─────────────────────────────────────────────────────────────
    // ADMIN: ACCOUNT STATUS
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public User suspendAccount(Long targetUserId) {
        User user = userRepository.findActiveById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (user.isAdmin() || user.isSuperAdmin()) {
            throw new AuthException("Admin accounts cannot be suspended via this endpoint", "FORBIDDEN");
        }

        user.setAccountStatus(AccountStatus.SUSPENDED);
        User saved = userRepository.save(user);
        log.info("Account suspended — userId={}", targetUserId);
        return saved;
    }

    @Transactional
    public User reactivateAccount(Long targetUserId) {
        User user = userRepository.findById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        if (Boolean.TRUE.equals(user.getIsDeleted())) {
            throw new AuthException("Cannot reactivate a deleted account", "ACCOUNT_DELETED");
        }

        user.setAccountStatus(AccountStatus.ACTIVE);
        User saved = userRepository.save(user);
        log.info("Account reactivated — userId={}", targetUserId);
        return saved;
    }

    @Transactional
    public void softDeleteAccount(Long targetUserId, Long requestingUserId, boolean isAdmin) {
        if (!isAdmin && !targetUserId.equals(requestingUserId)) {
            throw new AuthException("Not authorized to delete this account", "FORBIDDEN");
        }

        User user = userRepository.findActiveById(targetUserId)
                .orElseThrow(() -> new UserNotFoundException(targetUserId));

        user.setIsDeleted(true);
        user.setDeletedAt(LocalDateTime.now());
        user.setAccountStatus(AccountStatus.DELETED);
        userRepository.save(user);
        log.info("Account soft-deleted — userId={}", targetUserId);
    }

    // ─────────────────────────────────────────────────────────────
    // ADMIN: LIST USERS
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<User> getAllUsers() {
        return userRepository.findByUserRoleAndIsDeletedFalse(UserRole.CUSTOMER);
    }

    @Transactional(readOnly = true)
    public List<User> getAllAdmins() {
        return userRepository.findByUserRoleAndIsDeletedFalse(UserRole.ADMIN);
    }
}