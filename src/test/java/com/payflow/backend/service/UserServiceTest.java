package com.payflow.backend.service;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.PaymentMethod;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.exception.AuthException;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .id(1L)
                .email("user@test.com")
                .firstName("John")
                .lastName("Doe")
                .passwordHash("hashed-password")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
    }

    // ==========================================================
    // getProfile
    // ==========================================================

    @Test
    void shouldReturnOwnProfile() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        User result = userService.getProfile(1L, 1L, false);

        assertEquals(user, result);
    }

    @Test
    void shouldReturnProfileForAdmin() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        User result = userService.getProfile(1L, 999L, true);

        assertEquals(user, result);
    }

    @Test
    void shouldThrowWhenViewingAnotherUserProfile() {
        assertThrows(
                AuthException.class,
                () -> userService.getProfile(2L, 1L, false)
        );
    }

    @Test
    void shouldThrowWhenProfileNotFound() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.getProfile(1L, 1L, false)
        );
    }

    // ==========================================================
    // updateProfile
    // ==========================================================

    @Test
    void shouldUpdateProfileSuccessfully() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.save(any(User.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        User result = userService.updateProfile(
                1L,
                "Updated",
                "User",
                "0911000000",
                "Street",
                "City",
                "State",
                "1000",
                "Ethiopia",
                PaymentMethod.STRIPE,
                Currency.USD
        );

        assertEquals("Updated", result.getFirstName());
        assertEquals("User", result.getLastName());
        assertEquals("0911000000", result.getPhone());

        verify(userRepository).save(user);
    }

    @Test
    void shouldIgnoreBlankFirstName() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        userService.updateProfile(
                1L,
                "",
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );

        assertEquals("John", user.getFirstName());
    }

    @Test
    void shouldThrowWhenUpdatingMissingUser() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.updateProfile(
                        1L,
                        "A",
                        "B",
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );
    }

    // ==========================================================
    // changePassword
    // ==========================================================

    @Test
    void shouldChangePasswordSuccessfully() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(
                "oldPassword",
                "hashed-password"))
                .thenReturn(true);

        when(passwordEncoder.encode("newPassword123"))
                .thenReturn("newHash");

        userService.changePassword(
                1L,
                "oldPassword",
                "newPassword123",
                "newPassword123"
        );

        assertEquals("newHash", user.getPasswordHash());

        verify(userRepository).save(user);
    }

    @Test
    void shouldThrowWhenPasswordsDoNotMatch() {
        assertThrows(
                AuthException.class,
                () -> userService.changePassword(
                        1L,
                        "old",
                        "newPassword123",
                        "differentPassword"
                )
        );
    }

    @Test
    void shouldThrowWhenPasswordTooShort() {
        assertThrows(
                AuthException.class,
                () -> userService.changePassword(
                        1L,
                        "old",
                        "123",
                        "123"
                )
        );
    }

    @Test
    void shouldThrowWhenCurrentPasswordIncorrect() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(passwordEncoder.matches(any(), any()))
                .thenReturn(false);

        assertThrows(
                AuthException.class,
                () -> userService.changePassword(
                        1L,
                        "wrong",
                        "newPassword123",
                        "newPassword123"
                )
        );
    }

    @Test
    void shouldThrowWhenUserMissingDuringPasswordChange() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.changePassword(
                        1L,
                        "old",
                        "newPassword123",
                        "newPassword123"
                )
        );
    }

    // ==========================================================
    // suspendAccount
    // ==========================================================

    @Test
    void shouldSuspendCustomerAccount() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        User result = userService.suspendAccount(1L);

        assertEquals(
                AccountStatus.SUSPENDED,
                result.getAccountStatus()
        );
    }

    @Test
    void shouldThrowWhenSuspendingAdmin() {
        user.setUserRole(UserRole.ADMIN);

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        assertThrows(
                AuthException.class,
                () -> userService.suspendAccount(1L)
        );
    }

    @Test
    void shouldThrowWhenSuspendingMissingUser() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.suspendAccount(1L)
        );
    }

    // ==========================================================
    // reactivateAccount
    // ==========================================================

    @Test
    void shouldReactivateAccount() {
        user.setAccountStatus(AccountStatus.SUSPENDED);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        when(userRepository.save(any(User.class)))
                .thenReturn(user);

        User result = userService.reactivateAccount(1L);

        assertEquals(
                AccountStatus.ACTIVE,
                result.getAccountStatus()
        );
    }

    @Test
    void shouldThrowWhenReactivatingDeletedAccount() {
        user.setIsDeleted(true);

        when(userRepository.findById(1L))
                .thenReturn(Optional.of(user));

        assertThrows(
                AuthException.class,
                () -> userService.reactivateAccount(1L)
        );
    }

    @Test
    void shouldThrowWhenReactivatingMissingUser() {
        when(userRepository.findById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.reactivateAccount(1L)
        );
    }

    // ==========================================================
    // softDeleteAccount
    // ==========================================================

    @Test
    void shouldSoftDeleteOwnAccount() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        userService.softDeleteAccount(
                1L,
                1L,
                false
        );

        assertTrue(user.getIsDeleted());

        assertEquals(
                AccountStatus.DELETED,
                user.getAccountStatus()
        );

        assertNotNull(user.getDeletedAt());

        verify(userRepository).save(user);
    }

    @Test
    void shouldAllowAdminToDeleteAnyAccount() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        userService.softDeleteAccount(
                1L,
                999L,
                true
        );

        verify(userRepository).save(user);
    }

    @Test
    void shouldThrowWhenDeletingAnotherUsersAccount() {
        assertThrows(
                AuthException.class,
                () -> userService.softDeleteAccount(
                        2L,
                        1L,
                        false
                )
        );
    }

    @Test
    void shouldThrowWhenDeletingMissingUser() {
        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> userService.softDeleteAccount(
                        1L,
                        1L,
                        false
                )
        );
    }

    // ==========================================================
    // getAllUsers
    // ==========================================================

    @Test
    void shouldReturnAllUsers() {
        when(userRepository.findByUserRoleAndIsDeletedFalse(UserRole.CUSTOMER))
                .thenReturn(List.of(user));

        List<User> result = userService.getAllUsers();

        assertEquals(1, result.size());
    }

    // ==========================================================
    // getAllAdmins
    // ==========================================================

    @Test
    void shouldReturnAllAdmins() {
        User admin = User.builder()
                .id(2L)
                .email("admin@test.com")
                .userRole(UserRole.ADMIN)
                .build();

        when(userRepository.findByUserRoleAndIsDeletedFalse(UserRole.ADMIN))
                .thenReturn(List.of(admin));

        List<User> result = userService.getAllAdmins();

        assertEquals(1, result.size());
        assertEquals(UserRole.ADMIN, result.get(0).getUserRole());
    }
}

