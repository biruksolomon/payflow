package com.payflow.backend.config;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * SuperAdminInitializer ensures that exactly one SUPER_ADMIN account exists
 * every time the application starts.
 *
 * Credentials are sourced from application.yml / environment variables:
 *   app.super-admin.email    (or SUPER_ADMIN_EMAIL env var)
 *   app.super-admin.password (or SUPER_ADMIN_PASSWORD env var)
 *
 * Behaviour:
 *  - If no SUPER_ADMIN with the configured email exists → creates one.
 *  - If the SUPER_ADMIN already exists → ensures it is ACTIVE and not soft-deleted.
 *  - The password is re-encoded from the configured plain-text value on every startup
 *    so that rotating credentials in properties/env immediately takes effect.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SuperAdminInitializer implements ApplicationRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.super-admin.email}")
    private String superAdminEmail;

    @Value("${app.super-admin.password}")
    private String superAdminPassword;

    @Value("${app.super-admin.first-name}")
    private String superAdminFirstName;

    @Value("${app.super-admin.last-name}")
    private String superAdminLastName;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        log.info("Checking super admin account: {}", superAdminEmail);

        userRepository.findByEmail(superAdminEmail).ifPresentOrElse(
                this::ensureSuperAdminActive,
                this::createSuperAdmin
        );
    }

    private void ensureSuperAdminActive(User existing) {
        boolean needsSave = false;

        if (existing.getUserRole() != UserRole.SUPER_ADMIN) {
            existing.setUserRole(UserRole.SUPER_ADMIN);
            needsSave = true;
        }
        if (Boolean.TRUE.equals(existing.getIsDeleted())) {
            existing.setIsDeleted(false);
            existing.setDeletedAt(null);
            needsSave = true;
        }
        if (existing.getAccountStatus() != AccountStatus.ACTIVE) {
            existing.setAccountStatus(AccountStatus.ACTIVE);
            needsSave = true;
        }
        if (!existing.getEmailVerified()) {
            existing.setEmailVerified(true);
            needsSave = true;
        }
        // Always re-encode the password from the configured value so that
        // credential rotation in properties/env is picked up at restart.
        existing.setPasswordHash(passwordEncoder.encode(superAdminPassword));
        needsSave = true;

        if (needsSave) {
            userRepository.save(existing);
            log.info("Super admin account updated and verified: {}", superAdminEmail);
        } else {
            log.info("Super admin account is healthy, no changes needed: {}", superAdminEmail);
        }
    }

    private void createSuperAdmin() {
        User superAdmin = User.builder()
                .email(superAdminEmail)
                .passwordHash(passwordEncoder.encode(superAdminPassword))
                .firstName(superAdminFirstName)
                .lastName(superAdminLastName)
                .userRole(UserRole.SUPER_ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();

        userRepository.save(superAdmin);
        log.info("Super admin account created: {}", superAdminEmail);
    }
}
