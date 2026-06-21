package com.payflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.config.SecurityConfig;
import com.payflow.backend.config.TestWebMvcSecurityConfig;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.UserNotFoundException;
import org.springframework.http.HttpStatus;
import com.payflow.backend.security.JwtAuthenticationFilter;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = UserController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestWebMvcSecurityConfig.class)
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserService userService;

    private User customer;
    private User adminUser;
    private PayFlowUserDetails customerDetails;
    private UsernamePasswordAuthenticationToken customerToken;
    private PayFlowUserDetails adminDetails;
    private UsernamePasswordAuthenticationToken adminToken;

    @BeforeEach
    void setUp() {
        customer = User.builder()
                .id(1L)
                .email("customer@test.com")
                .passwordHash("hashed")
                .firstName("John")
                .lastName("Doe")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
        customerDetails = new PayFlowUserDetails(customer);
        customerToken = new UsernamePasswordAuthenticationToken(customerDetails, null, customerDetails.getAuthorities());

        adminUser = User.builder()
                .id(99L)
                .email("admin@test.com")
                .passwordHash("hashed")
                .firstName("Admin")
                .lastName("User")
                .userRole(UserRole.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
        adminDetails = new PayFlowUserDetails(adminUser);
        adminToken = new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
    }

    // ─────────────────────────────────────────────────────────────
    // GET MY PROFILE
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnOwnProfile() throws Exception {
        when(userService.getProfile(1L, 1L, false)).thenReturn(customer);

        mockMvc.perform(get("/api/users/me")
                        .with(authentication(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@test.com"))
                .andExpect(jsonPath("$.firstName").value("John"));

        verify(userService).getProfile(1L, 1L, false);
    }

    // ─────────────────────────────────────────────────────────────
    // GET PROFILE BY ID
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldAllowAdminToGetAnyProfile() throws Exception {
        when(userService.getProfile(1L, 99L, true)).thenReturn(customer);

        mockMvc.perform(get("/api/users/1")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("customer@test.com"));

        verify(userService).getProfile(1L, 99L, true);
    }

    @Test
    void shouldReturn403WhenCustomerGetsOtherProfile() throws Exception {
        when(userService.getProfile(2L, 1L, false))
                .thenThrow(new AuthException("Not authorized to view this profile", "FORBIDDEN", HttpStatus.FORBIDDEN));

        mockMvc.perform(get("/api/users/2")
                        .with(authentication(customerToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn404WhenUserNotFound() throws Exception {
        when(userService.getProfile(999L, 99L, true))
                .thenThrow(new UserNotFoundException(999L));

        mockMvc.perform(get("/api/users/999")
                        .with(authentication(adminToken)))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE MY PROFILE
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldUpdateProfileSuccessfully() throws Exception {
        User updated = User.builder().id(1L).email("customer@test.com")
                .firstName("Jane").lastName("Doe").userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE).emailVerified(true).isDeleted(false).build();

        when(userService.updateProfile(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(updated);

        mockMvc.perform(put("/api/users/me")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("firstName", "Jane"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Jane"));

        verify(userService).updateProfile(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ─────────────────────────────────────────────────────────────
    // CHANGE PASSWORD
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldChangePasswordSuccessfully() throws Exception {
        doNothing().when(userService).changePassword(1L, "OldPass1!", "NewPass1!", "NewPass1!");

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "OldPass1!",
                                "newPassword", "NewPass1!",
                                "confirmNewPassword", "NewPass1!"
                        ))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password changed successfully"));

        verify(userService).changePassword(1L, "OldPass1!", "NewPass1!", "NewPass1!");
    }

    @Test
    void shouldReturn400WhenPasswordFieldsMissing() throws Exception {
        mockMvc.perform(post("/api/users/me/change-password")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("newPassword", "NewPass1!"))))
                .andExpect(status().isBadRequest());

        verify(userService, never()).changePassword(any(), any(), any(), any());
    }

    @Test
    void shouldReturn400WhenPasswordsDoNotMatch() throws Exception {
        doThrow(new AuthException("New passwords do not match", "PASSWORD_MISMATCH", HttpStatus.BAD_REQUEST))
                .when(userService).changePassword(1L, "OldPass1!", "NewPass1!", "Different1!");

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "OldPass1!",
                                "newPassword", "NewPass1!",
                                "confirmNewPassword", "Different1!"
                        ))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturn400WhenCurrentPasswordWrong() throws Exception {
        doThrow(new AuthException("Current password is incorrect", "INVALID_CURRENT_PASSWORD", HttpStatus.BAD_REQUEST))
                .when(userService).changePassword(1L, "WrongPass!", "NewPass1!", "NewPass1!");

        mockMvc.perform(post("/api/users/me/change-password")
                        .with(authentication(customerToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "currentPassword", "WrongPass!",
                                "newPassword", "NewPass1!",
                                "confirmNewPassword", "NewPass1!"
                        ))))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────
    // SUSPEND / REACTIVATE (admin)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldSuspendAccountSuccessfully() throws Exception {
        User suspended = User.builder().id(1L).email("customer@test.com")
                .firstName("John").lastName("Doe").userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.SUSPENDED).emailVerified(true).isDeleted(false).build();
        when(userService.suspendAccount(1L)).thenReturn(suspended);

        mockMvc.perform(post("/api/users/1/suspend")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("SUSPENDED"));

        verify(userService).suspendAccount(1L);
    }

    @Test
    void shouldReturn403WhenSuspendingAdminAccount() throws Exception {
        when(userService.suspendAccount(99L))
                .thenThrow(new AuthException("Admin accounts cannot be suspended", "FORBIDDEN", HttpStatus.FORBIDDEN));

        mockMvc.perform(post("/api/users/99/suspend")
                        .with(authentication(adminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReactivateAccountSuccessfully() throws Exception {
        User reactivated = User.builder().id(1L).email("customer@test.com")
                .firstName("John").lastName("Doe").userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE).emailVerified(true).isDeleted(false).build();
        when(userService.reactivateAccount(1L)).thenReturn(reactivated);

        mockMvc.perform(post("/api/users/1/reactivate")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        verify(userService).reactivateAccount(1L);
    }

    // ─────────────────────────────────────────────────────────────
    // SOFT DELETE
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldDeleteOwnAccountSuccessfully() throws Exception {
        doNothing().when(userService).softDeleteAccount(1L, 1L, false);

        mockMvc.perform(delete("/api/users/1")
                        .with(authentication(customerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deleted successfully"));

        verify(userService).softDeleteAccount(1L, 1L, false);
    }

    @Test
    void shouldAllowAdminToDeleteAnyAccount() throws Exception {
        doNothing().when(userService).softDeleteAccount(1L, 99L, true);

        mockMvc.perform(delete("/api/users/1")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deleted successfully"));

        verify(userService).softDeleteAccount(1L, 99L, true);
    }

    @Test
    void shouldReturn403WhenCustomerDeletesOtherAccount() throws Exception {
        doThrow(new AuthException("Not authorized to delete this account", "FORBIDDEN", HttpStatus.FORBIDDEN))
                .when(userService).softDeleteAccount(2L, 1L, false);

        mockMvc.perform(delete("/api/users/2")
                        .with(authentication(customerToken)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────
    // ADMIN LISTS
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnAllCustomers() throws Exception {
        when(userService.getAllUsers()).thenReturn(List.of(customer));

        mockMvc.perform(get("/api/users/admin/customers")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("customer@test.com"))
                .andExpect(jsonPath("$[0].userRole").value("CUSTOMER"));

        verify(userService).getAllUsers();
    }

    @Test
    void shouldReturnAllAdmins() throws Exception {
        when(userService.getAllAdmins()).thenReturn(List.of(adminUser));

        mockMvc.perform(get("/api/users/admin/admins")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@test.com"))
                .andExpect(jsonPath("$[0].userRole").value("ADMIN"));

        verify(userService).getAllAdmins();
    }
}
