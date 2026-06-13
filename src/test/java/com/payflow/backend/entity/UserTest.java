package com.payflow.backend.entity;

import com.payflow.backend.domain.entity.User;
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
                .userRole("ADMIN")
                .build();

        assertTrue(user.isAdmin());
    }

    @Test
    @DisplayName("Should return false when role is CUSTOMER")
    void shouldReturnFalseForCustomerUser() {

        User user = User.builder()
                .userRole("CUSTOMER")
                .build();

        assertFalse(user.isAdmin());
    }

    @Test
    @DisplayName("Should return true when account is active and not deleted")
    void shouldReturnTrueForActiveUser() {

        User user = User.builder()
                .accountStatus("ACTIVE")
                .isDeleted(false)
                .build();

        assertTrue(user.isActive());
    }

    @Test
    @DisplayName("Should return false when account is suspended")
    void shouldReturnFalseForSuspendedUser() {

        User user = User.builder()
                .accountStatus("SUSPENDED")
                .isDeleted(false)
                .build();

        assertFalse(user.isActive());
    }

    @Test
    @DisplayName("Should return false when account is deleted")
    void shouldReturnFalseForDeletedUser() {

        User user = User.builder()
                .accountStatus("ACTIVE")
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

        assertEquals("CUSTOMER", user.getUserRole());
        assertEquals("ACTIVE", user.getAccountStatus());
        assertFalse(user.getEmailVerified());
        assertEquals("USD", user.getPreferredCurrency());
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
                .userRole("ADMIN")
                .accountStatus("SUSPENDED")
                .emailVerified(true)
                .preferredCurrency("EUR")
                .isDeleted(true)
                .build();

        assertEquals("ADMIN", user.getUserRole());
        assertEquals("SUSPENDED", user.getAccountStatus());
        assertTrue(user.getEmailVerified());
        assertEquals("EUR", user.getPreferredCurrency());
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
