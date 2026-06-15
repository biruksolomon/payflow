package com.payflow.backend.security;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    private User user;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash("encoded-password")
                .firstName("John")
                .lastName("Doe")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .build();
    }

    @Test
    void shouldLoadUserByUsernameSuccessfully() {

        when(userRepository.findActiveByEmail(user.getEmail()))
                .thenReturn(Optional.of(user));

        UserDetails result =
                customUserDetailsService.loadUserByUsername(user.getEmail());

        assertNotNull(result);
        assertInstanceOf(PayFlowUserDetails.class, result);

        assertEquals(user.getEmail(), result.getUsername());
        assertEquals(user.getPasswordHash(), result.getPassword());

        verify(userRepository)
                .findActiveByEmail(user.getEmail());
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundByUsername() {

        when(userRepository.findActiveByEmail("missing@test.com"))
                .thenReturn(Optional.empty());

        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> customUserDetailsService
                                .loadUserByUsername("missing@test.com")
                );

        assertTrue(
                exception.getMessage()
                        .contains("User not found with email")
        );

        verify(userRepository)
                .findActiveByEmail("missing@test.com");
    }

    @Test
    void shouldWrapUnexpectedExceptionWhenLoadingByUsername() {

        when(userRepository.findActiveByEmail(anyString()))
                .thenThrow(new RuntimeException("Database error"));

        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> customUserDetailsService
                                .loadUserByUsername("user@test.com")
                );

        assertEquals(
                "Error loading user details",
                exception.getMessage()
        );

        assertNotNull(exception.getCause());
    }

    @Test
    void shouldLoadUserByIdSuccessfully() {

        when(userRepository.findActiveById(user.getId()))
                .thenReturn(Optional.of(user));

        UserDetails result =
                customUserDetailsService.loadUserById(user.getId());

        assertNotNull(result);
        assertInstanceOf(PayFlowUserDetails.class, result);

        assertEquals(user.getEmail(), result.getUsername());

        verify(userRepository)
                .findActiveById(user.getId());
    }

    @Test
    void shouldThrowExceptionWhenUserNotFoundById() {

        when(userRepository.findActiveById(999L))
                .thenReturn(Optional.empty());

        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> customUserDetailsService.loadUserById(999L)
                );

        assertTrue(
                exception.getMessage()
                        .contains("User not found with id")
        );

        verify(userRepository)
                .findActiveById(999L);
    }

    @Test
    void shouldWrapUnexpectedExceptionWhenLoadingById() {

        when(userRepository.findActiveById(anyLong()))
                .thenThrow(new RuntimeException("Database error"));

        UsernameNotFoundException exception =
                assertThrows(
                        UsernameNotFoundException.class,
                        () -> customUserDetailsService.loadUserById(1L)
                );

        assertEquals(
                "Error loading user details",
                exception.getMessage()
        );

        assertNotNull(exception.getCause());
    }
}