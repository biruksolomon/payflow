package com.payflow.backend.entity;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.Currency;
import com.payflow.backend.domain.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    @DisplayName("Should return full name")
    void shouldReturnFullName() {

        User user = User.builder()
                .firstName("Biruk")
                .lastName("Solomon")
                .build();

        assertEquals(
                "Biruk Solomon",
                user.getFullName()
        );
    }

    @Test
    @DisplayName("Should return true when role is ADMIN")
    void shouldReturnTrueForAdminUser() {

        User user = User.builder()
                .userRole(UserRole.ADMIN)
                .build();

        assertTrue(user.isAdmin());
    }

    @Test
    @DisplayName("Should return false when role is CUSTOMER")
    void shouldReturnFalseForCustomerUser() {

        User user = User.builder()
                .userRole(UserRole.CUSTOMER)
                .build();

        assertFalse(user.isAdmin());
    }

    @Test
    @DisplayName("Should return true when account is active and not deleted")
    void shouldReturnTrueForActiveUser() {

        User user = User.builder()
                .accountStatus(AccountStatus.ACTIVE)
                .isDeleted(false)
                .build();

        assertTrue(user.isActive());
    }

    @Test
    @DisplayName("Should return false when account is suspended")
    void shouldReturnFalseForSuspendedUser() {

        User user = User.builder()
                .accountStatus(AccountStatus.SUSPENDED)
                .isDeleted(false)
                .build();

        assertFalse(user.isActive());
    }

    @Test
    @DisplayName("Should return false when account is deleted")
    void shouldReturnFalseForDeletedUser() {

        User user = User.builder()
                .accountStatus(AccountStatus.ACTIVE)
                .isDeleted(true)
                .build();

        assertFalse(user.isActive());
    }

    @Test
    @DisplayName("Should apply builder default values")
    void shouldApplyDefaultValues() {

        User user = User.builder()
                .email("test@payflow.com")
                .passwordHash("passwordHash")
                .firstName("Biruk")
                .lastName("Solomon")
                .build();

        assertEquals(UserRole.CUSTOMER, user.getUserRole());
        assertEquals(AccountStatus.ACTIVE, user.getAccountStatus());
        assertFalse(user.getEmailVerified());
        assertEquals(Currency.USD, user.getPreferredCurrency());
        assertFalse(user.getIsDeleted());
    }

    @Test
    @DisplayName("Should override default values when explicitly provided")
    void shouldOverrideDefaultValues() {

        User user = User.builder()
                .email("admin@payflow.com")
                .passwordHash("passwordHash")
                .firstName("Admin")
                .lastName("User")
                .userRole(UserRole.ADMIN)
                .accountStatus(AccountStatus.SUSPENDED)
                .emailVerified(true)
                .preferredCurrency(Currency.EUR)
                .isDeleted(true)
                .build();

        assertEquals(UserRole.ADMIN, user.getUserRole());
        assertEquals(AccountStatus.SUSPENDED, user.getAccountStatus());
        assertTrue(user.getEmailVerified());
        assertEquals(Currency.EUR, user.getPreferredCurrency());
        assertTrue(user.getIsDeleted());
    }

    @Test
    @DisplayName("Should create user using no args constructor")
    void shouldCreateUserUsingNoArgsConstructor() {

        User user = new User();

        assertNotNull(user);
    }

    @Test
    @DisplayName("Should support equals and hashCode")
    void shouldSupportEqualsAndHashCode() {

        User user1 = User.builder()
                .id(1L)
                .email("biruk@payflow.com")
                .passwordHash("hash")
                .firstName("Biruk")
                .lastName("Solomon")
                .build();

        User user2 = User.builder()
                .id(1L)
                .email("biruk@payflow.com")
                .passwordHash("hash")
                .firstName("Biruk")
                .lastName("Solomon")
                .build();

        assertEquals(user1, user2);
        assertEquals(user1.hashCode(), user2.hashCode());
    }

    @Test
    @DisplayName("Should support toString")
    void shouldGenerateToString() {

        User user = User.builder()
                .email("biruk@payflow.com")
                .passwordHash("hash")
                .firstName("Biruk")
                .lastName("Solomon")
                .build();

        assertNotNull(user.toString());
    }


}
