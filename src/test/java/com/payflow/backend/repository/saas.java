package com.payflow.backend.repository;


import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    @DisplayName("Should find user by email")
    void shouldFindUserByEmail() {

        User user = User.builder()
                .email("biruk@test.com")
                .passwordHash("hash")
                .firstName("Biruk")
                .lastName("Solomon")
                .build();

        userRepository.save(user);

        Optional<User> result =
                userRepository.findByEmail("biruk@test.com");

        assertTrue(result.isPresent());

        assertEquals(
                "biruk@test.com",
                result.get().getEmail()
        );
    }

    @Test
    @DisplayName("Should return empty when email not found")
    void shouldReturnEmptyWhenEmailNotFound() {

        Optional<User> result =
                userRepository.findByEmail("missing@test.com");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should check if email exists")
    void shouldCheckEmailExists() {

        User user = User.builder()
                .email("exists@test.com")
                .passwordHash("hash")
                .firstName("Test")
                .lastName("User")
                .build();

        userRepository.save(user);

        assertTrue(
                userRepository.existsByEmail("exists@test.com")
        );
    }

    @Test
    @DisplayName("Should return false when email does not exist")
    void shouldReturnFalseWhenEmailDoesNotExist() {

        assertFalse(
                userRepository.existsByEmail("unknown@test.com")
        );
    }

    @Test
    @DisplayName("Should find active user by email")
    void shouldFindActiveUserByEmail() {

        User user = User.builder()
                .email("active@test.com")
                .passwordHash("hash")
                .firstName("Active")
                .lastName("User")
                .isDeleted(false)
                .build();

        userRepository.save(user);

        Optional<User> result =
                userRepository.findActiveByEmail("active@test.com");

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Should not find deleted user by active email query")
    void shouldNotFindDeletedUserByEmail() {

        User user = User.builder()
                .email("deleted@test.com")
                .passwordHash("hash")
                .firstName("Deleted")
                .lastName("User")
                .isDeleted(true)
                .build();

        userRepository.save(user);

        Optional<User> result =
                userRepository.findActiveByEmail("deleted@test.com");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find active user by id")
    void shouldFindActiveUserById() {

        User user = User.builder()
                .email("id@test.com")
                .passwordHash("hash")
                .firstName("Id")
                .lastName("User")
                .isDeleted(false)
                .build();

        User saved =
                userRepository.save(user);

        Optional<User> result =
                userRepository.findActiveById(saved.getId());

        assertTrue(result.isPresent());
    }

    @Test
    @DisplayName("Should not find deleted user by id")
    void shouldNotFindDeletedUserById() {

        User user = User.builder()
                .email("deletedid@test.com")
                .passwordHash("hash")
                .firstName("Deleted")
                .lastName("User")
                .isDeleted(true)
                .build();

        User saved =
                userRepository.save(user);

        Optional<User> result =
                userRepository.findActiveById(saved.getId());

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find users by role")
    void shouldFindUsersByRole() {

        User admin1 = User.builder()
                .email("admin1@test.com")
                .passwordHash("hash")
                .firstName("Admin")
                .lastName("One")
                .userRole(UserRole.ADMIN)
                .build();

        User admin2 = User.builder()
                .email("admin2@test.com")
                .passwordHash("hash")
                .firstName("Admin")
                .lastName("Two")
                .userRole(UserRole.ADMIN)
                .build();

        User customer = User.builder()
                .email("customer@test.com")
                .passwordHash("hash")
                .firstName("Customer")
                .lastName("One")
                .userRole(UserRole.CUSTOMER)
                .build();

        userRepository.save(admin1);
        userRepository.save(admin2);
        userRepository.save(customer);

        List<User> admins =
                userRepository.findByUserRoleAndIsDeletedFalse(
                        UserRole.ADMIN
                );

        assertEquals(2, admins.size());
    }

    @Test
    @DisplayName("Should not return deleted users in role search")
    void shouldExcludeDeletedUsersFromRoleSearch() {

        User activeAdmin = User.builder()
                .email("activeadmin@test.com")
                .passwordHash("hash")
                .firstName("Active")
                .lastName("Admin")
                .userRole(UserRole.ADMIN)
                .isDeleted(false)
                .build();

        User deletedAdmin = User.builder()
                .email("deletedadmin@test.com")
                .passwordHash("hash")
                .firstName("Deleted")
                .lastName("Admin")
                .userRole(UserRole.ADMIN)
                .isDeleted(true)
                .build();

        userRepository.save(activeAdmin);
        userRepository.save(deletedAdmin);

        List<User> admins =
                userRepository.findByUserRoleAndIsDeletedFalse(
                        UserRole.ADMIN
                );

        assertEquals(1, admins.size());
    }
}