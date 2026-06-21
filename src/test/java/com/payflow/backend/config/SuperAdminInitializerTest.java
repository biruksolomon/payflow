package com.payflow.backend.config;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.ApplicationArguments;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SuperAdminInitializerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private ApplicationArguments applicationArguments;

    @InjectMocks
    private SuperAdminInitializer superAdminInitializer;

    @BeforeEach
    void setUp() {
        // Inject @Value fields via ReflectionTestUtils (no Spring context needed)
        ReflectionTestUtils.setField(superAdminInitializer, "superAdminEmail",    "superadmin@test.com");
        ReflectionTestUtils.setField(superAdminInitializer, "superAdminPassword", "SuperAdmin@Test123");
        ReflectionTestUtils.setField(superAdminInitializer, "superAdminFirstName", "Super");
        ReflectionTestUtils.setField(superAdminInitializer, "superAdminLastName",  "Admin");
    }

    // ==========================================================
    // run — super admin does not exist yet
    // ==========================================================

    @Test
    void shouldCreateSuperAdminWhenNotExists() throws Exception {
        when(userRepository.findByEmail("superadmin@test.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode("SuperAdmin@Test123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1L);
            return u;
        });

        superAdminInitializer.run(applicationArguments);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User created = captor.getValue();
        assertEquals("superadmin@test.com", created.getEmail());
        assertEquals(UserRole.SUPER_ADMIN,   created.getUserRole());
        assertEquals(AccountStatus.ACTIVE,   created.getAccountStatus());
        assertTrue(created.getEmailVerified());
        assertFalse(created.getIsDeleted());
        assertEquals("encodedPassword", created.getPasswordHash());
    }

    // ==========================================================
    // run — super admin already exists and is healthy
    // ==========================================================

    @Test
    void shouldUpdatePasswordWhenSuperAdminAlreadyExists() throws Exception {
        User existing = User.builder()
                .id(1L)
                .email("superadmin@test.com")
                .passwordHash("oldHash")
                .firstName("Super")
                .lastName("Admin")
                .userRole(UserRole.SUPER_ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();

        when(userRepository.findByEmail("superadmin@test.com")).thenReturn(Optional.of(existing));
        when(passwordEncoder.encode("SuperAdmin@Test123")).thenReturn("newHash");

        superAdminInitializer.run(applicationArguments);

        // Password must be re-encoded from configured value
        assertEquals("newHash", existing.getPasswordHash());
        verify(userRepository).save(existing);
    }

    // ==========================================================
    // run — super admin exists but is soft-deleted
    // ==========================================================

    @Test
    void shouldRestoreDeletedSuperAdmin() throws Exception {
        User deleted = User.builder()
                .id(1L)
                .email("superadmin@test.com")
                .passwordHash("hashed")
                .firstName("Super")
                .lastName("Admin")
                .userRole(UserRole.SUPER_ADMIN)
                .accountStatus(AccountStatus.DELETED)
                .emailVerified(true)
                .isDeleted(true)
                .build();

        when(userRepository.findByEmail("superadmin@test.com")).thenReturn(Optional.of(deleted));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        superAdminInitializer.run(applicationArguments);

        assertFalse(deleted.getIsDeleted());
        assertNull(deleted.getDeletedAt());
        assertEquals(AccountStatus.ACTIVE, deleted.getAccountStatus());
        verify(userRepository).save(deleted);
    }

    // ==========================================================
    // run — super admin exists but role was changed
    // ==========================================================

    @Test
    void shouldRestoreRoleWhenChangedFromSuperAdmin() throws Exception {
        User tampered = User.builder()
                .id(1L)
                .email("superadmin@test.com")
                .passwordHash("hashed")
                .firstName("Super")
                .lastName("Admin")
                .userRole(UserRole.ADMIN)         // role was changed
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();

        when(userRepository.findByEmail("superadmin@test.com")).thenReturn(Optional.of(tampered));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        superAdminInitializer.run(applicationArguments);

        assertEquals(UserRole.SUPER_ADMIN, tampered.getUserRole());
        verify(userRepository).save(tampered);
    }

    // ==========================================================
    // run — super admin exists but emailVerified was reset
    // ==========================================================

    @Test
    void shouldRestoreEmailVerifiedFlag() throws Exception {
        User unverified = User.builder()
                .id(1L)
                .email("superadmin@test.com")
                .passwordHash("hashed")
                .firstName("Super")
                .lastName("Admin")
                .userRole(UserRole.SUPER_ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(false)             // flag reset
                .isDeleted(false)
                .build();

        when(userRepository.findByEmail("superadmin@test.com")).thenReturn(Optional.of(unverified));
        when(passwordEncoder.encode(anyString())).thenReturn("encodedPassword");

        superAdminInitializer.run(applicationArguments);

        assertTrue(unverified.getEmailVerified());
        verify(userRepository).save(unverified);
    }
}
