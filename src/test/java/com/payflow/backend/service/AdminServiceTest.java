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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AdminService adminService;

    private User customer;
    private User admin;
    private User superAdmin;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(1L)
                .email("customer@test.com")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("hashed")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();

        admin = User.builder()
                .id(2L)
                .email("admin@test.com")
                .firstName("Admin")
                .lastName("User")
                .passwordHash("hashed")
                .userRole(UserRole.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();

        superAdmin = User.builder()
                .id(3L)
                .email("super@test.com")
                .firstName("Super")
                .lastName("Admin")
                .passwordHash("hashed")
                .userRole(UserRole.SUPER_ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
    }

    // ==========================================================
    // createAdmin
    // ==========================================================

    @Test
    void shouldCreateAdminSuccessfully() {
        CreateAdminRequest request = CreateAdminRequest.builder()
                .email("newadmin@test.com")
                .password("Admin@123")
                .passwordConfirm("Admin@123")
                .firstName("New")
                .lastName("Admin")
                .build();

        when(userRepository.existsByEmail("newadmin@test.com")).thenReturn(false);
        when(passwordEncoder.encode("Admin@123")).thenReturn("encodedPassword");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(10L);
            return u;
        });

        User result = adminService.createAdmin(request);

        assertNotNull(result);
        assertEquals(UserRole.ADMIN, result.getUserRole());
        assertEquals("newadmin@test.com", result.getEmail());
        assertTrue(result.getEmailVerified());
        assertEquals(AccountStatus.ACTIVE, result.getAccountStatus());

        verify(userRepository).save(any(User.class));
    }

    @Test
    void shouldThrowWhenCreatingAdminWithPasswordMismatch() {
        CreateAdminRequest request = CreateAdminRequest.builder()
                .email("newadmin@test.com")
                .password("Admin@123")
                .passwordConfirm("Different@123")
                .firstName("New")
                .lastName("Admin")
                .build();

        assertThrows(PasswordMismatchException.class, () -> adminService.createAdmin(request));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenCreatingAdminWithDuplicateEmail() {
        CreateAdminRequest request = CreateAdminRequest.builder()
                .email("admin@test.com")
                .password("Admin@123")
                .passwordConfirm("Admin@123")
                .firstName("New")
                .lastName("Admin")
                .build();

        when(userRepository.existsByEmail("admin@test.com")).thenReturn(true);

        assertThrows(DuplicateEmailException.class, () -> adminService.createAdmin(request));

        verify(userRepository, never()).save(any());
    }

    // ==========================================================
    // deleteAdmin
    // ==========================================================

    @Test
    void shouldDeleteAdminSuccessfully() {
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(admin));

        adminService.deleteAdmin(2L);

        assertTrue(admin.getIsDeleted());
        assertEquals(AccountStatus.DELETED, admin.getAccountStatus());
        assertNotNull(admin.getDeletedAt());
        verify(userRepository).save(admin);
    }

    @Test
    void shouldThrowWhenDeletingSuperAdmin() {
        when(userRepository.findActiveById(3L)).thenReturn(Optional.of(superAdmin));

        assertThrows(AuthException.class, () -> adminService.deleteAdmin(3L));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDeletingNonAdminUser() {
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(customer));

        assertThrows(AuthException.class, () -> adminService.deleteAdmin(1L));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDeletingMissingAdmin() {
        when(userRepository.findActiveById(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> adminService.deleteAdmin(999L));
    }

    // ==========================================================
    // promoteToAdmin
    // ==========================================================

    @Test
    void shouldPromoteCustomerToAdmin() {
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(customer));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = adminService.promoteToAdmin(1L);

        assertEquals(UserRole.ADMIN, result.getUserRole());
        verify(userRepository).save(customer);
    }

    @Test
    void shouldThrowWhenPromotingAlreadyAdmin() {
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(admin));

        assertThrows(AuthException.class, () -> adminService.promoteToAdmin(2L));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPromotingSuperAdmin() {
        when(userRepository.findActiveById(3L)).thenReturn(Optional.of(superAdmin));

        assertThrows(AuthException.class, () -> adminService.promoteToAdmin(3L));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenPromotingMissingUser() {
        when(userRepository.findActiveById(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> adminService.promoteToAdmin(999L));
    }

    // ==========================================================
    // demoteToCustomer
    // ==========================================================

    @Test
    void shouldDemoteAdminToCustomer() {
        when(userRepository.findActiveById(2L)).thenReturn(Optional.of(admin));
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = adminService.demoteToCustomer(2L);

        assertEquals(UserRole.CUSTOMER, result.getUserRole());
        verify(userRepository).save(admin);
    }

    @Test
    void shouldThrowWhenDemotingSuperAdmin() {
        when(userRepository.findActiveById(3L)).thenReturn(Optional.of(superAdmin));

        assertThrows(AuthException.class, () -> adminService.demoteToCustomer(3L));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDemotingNonAdminUser() {
        when(userRepository.findActiveById(1L)).thenReturn(Optional.of(customer));

        assertThrows(AuthException.class, () -> adminService.demoteToCustomer(1L));

        verify(userRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDemotingMissingUser() {
        when(userRepository.findActiveById(999L)).thenReturn(Optional.empty());

        assertThrows(UserNotFoundException.class, () -> adminService.demoteToCustomer(999L));
    }

    // ==========================================================
    // getAllAdmins
    // ==========================================================

    @Test
    void shouldReturnAllAdmins() {
        when(userRepository.findByUserRoleAndIsDeletedFalse(UserRole.ADMIN))
                .thenReturn(List.of(admin));

        List<User> result = adminService.getAllAdmins();

        assertEquals(1, result.size());
        assertEquals(UserRole.ADMIN, result.get(0).getUserRole());
    }

    // ==========================================================
    // getAllSuperAdmins
    // ==========================================================

    @Test
    void shouldReturnAllSuperAdmins() {
        when(userRepository.findByUserRoleAndIsDeletedFalse(UserRole.SUPER_ADMIN))
                .thenReturn(List.of(superAdmin));

        List<User> result = adminService.getAllSuperAdmins();

        assertEquals(1, result.size());
        assertEquals(UserRole.SUPER_ADMIN, result.get(0).getUserRole());
    }
}
