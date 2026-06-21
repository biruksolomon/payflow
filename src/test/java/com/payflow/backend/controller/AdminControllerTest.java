package com.payflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.config.SecurityConfig;
import com.payflow.backend.config.TestWebMvcSecurityConfig;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.dto.request.CreateAdminRequest;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.DuplicateEmailException;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.security.JwtAuthenticationFilter;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.AdminService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = AdminController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestWebMvcSecurityConfig.class)
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdminService adminService;

    private User adminUser;
    private User superAdminUser;

    private UsernamePasswordAuthenticationToken superAdminToken;

    private UsernamePasswordAuthenticationToken adminToken;

    @BeforeEach
    void setUp() {
        superAdminUser = User.builder()
                .id(1L)
                .email("superadmin@test.com")
                .passwordHash("hashed")
                .firstName("Super")
                .lastName("Admin")
                .userRole(UserRole.SUPER_ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
        PayFlowUserDetails superAdminDetails = new PayFlowUserDetails(superAdminUser);
        superAdminToken = new UsernamePasswordAuthenticationToken(
                superAdminDetails, null, superAdminDetails.getAuthorities());

        adminUser = User.builder()
                .id(2L)
                .email("admin@test.com")
                .passwordHash("hashed")
                .firstName("Admin")
                .lastName("User")
                .userRole(UserRole.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
        PayFlowUserDetails adminDetails = new PayFlowUserDetails(adminUser);
        adminToken = new UsernamePasswordAuthenticationToken(
                adminDetails, null, adminDetails.getAuthorities());

        User customerUser = User.builder()
                .id(3L)
                .email("customer@test.com")
                .passwordHash("hashed")
                .firstName("Customer")
                .lastName("User")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/super-admin/admins — createAdmin
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldCreateAdminSuccessfully() throws Exception {
        CreateAdminRequest request = CreateAdminRequest.builder()
                .email("newadmin@test.com")
                .password("Admin@123")
                .passwordConfirm("Admin@123")
                .firstName("New")
                .lastName("Admin")
                .build();

        User newAdmin = User.builder()
                .id(10L)
                .email("newadmin@test.com")
                .firstName("New").lastName("Admin")
                .userRole(UserRole.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true).isDeleted(false)
                .build();

        when(adminService.createAdmin(any(CreateAdminRequest.class))).thenReturn(newAdmin);

        mockMvc.perform(post("/api/super-admin/admins")
                        .with(authentication(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("newadmin@test.com"))
                .andExpect(jsonPath("$.userRole").value("ADMIN"));

        verify(adminService).createAdmin(any(CreateAdminRequest.class));
    }

    @Test
    void shouldReturn403WhenNonSuperAdminCreatesAdmin() throws Exception {
        CreateAdminRequest request = CreateAdminRequest.builder()
                .email("newadmin@test.com")
                .password("Admin@123")
                .passwordConfirm("Admin@123")
                .firstName("New")
                .lastName("Admin")
                .build();

        mockMvc.perform(post("/api/super-admin/admins")
                        .with(authentication(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        verify(adminService, never()).createAdmin(any());
    }

    @Test
    void shouldReturn401WhenUnauthenticatedCreatesAdmin() throws Exception {
        CreateAdminRequest request = CreateAdminRequest.builder()
                .email("newadmin@test.com")
                .password("Admin@123")
                .passwordConfirm("Admin@123")
                .firstName("New")
                .lastName("Admin")
                .build();

        mockMvc.perform(post("/api/super-admin/admins")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());

        verify(adminService, never()).createAdmin(any());
    }

    @Test
    void shouldReturn409WhenDuplicateEmailOnCreateAdmin() throws Exception {
        CreateAdminRequest request = CreateAdminRequest.builder()
                .email("admin@test.com")
                .password("Admin@123")
                .passwordConfirm("Admin@123")
                .firstName("New")
                .lastName("Admin")
                .build();

        when(adminService.createAdmin(any())).thenThrow(new DuplicateEmailException("admin@test.com"));

        mockMvc.perform(post("/api/super-admin/admins")
                        .with(authentication(superAdminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());

        verify(adminService).createAdmin(any());
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE /api/super-admin/admins/{id}
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldDeleteAdminSuccessfully() throws Exception {
        doNothing().when(adminService).deleteAdmin(2L);

        mockMvc.perform(delete("/api/super-admin/admins/2")
                        .with(authentication(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Admin account deleted successfully"));

        verify(adminService).deleteAdmin(2L);
    }

    @Test
    void shouldReturn403WhenDeletingSuperAdminAccount() throws Exception {
        doThrow(new AuthException(
                "Super admin accounts cannot be deleted via this endpoint",
                "FORBIDDEN",
                HttpStatus.FORBIDDEN))
                .when(adminService).deleteAdmin(1L);

        mockMvc.perform(delete("/api/super-admin/admins/1")
                        .with(authentication(superAdminToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn404WhenDeletingMissingAdmin() throws Exception {
        doThrow(new UserNotFoundException(999L)).when(adminService).deleteAdmin(999L);

        mockMvc.perform(delete("/api/super-admin/admins/999")
                        .with(authentication(superAdminToken)))
                .andExpect(status().isNotFound());
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/super-admin/users/{id}/promote
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldPromoteCustomerToAdmin() throws Exception {
        User promoted = User.builder()
                .id(3L).email("customer@test.com")
                .firstName("Customer").lastName("User")
                .userRole(UserRole.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true).isDeleted(false)
                .build();

        when(adminService.promoteToAdmin(3L)).thenReturn(promoted);

        mockMvc.perform(post("/api/super-admin/users/3/promote")
                        .with(authentication(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userRole").value("ADMIN"));

        verify(adminService).promoteToAdmin(3L);
    }

    @Test
    void shouldReturn400WhenPromotingAlreadyAdmin() throws Exception {
        when(adminService.promoteToAdmin(2L))
                .thenThrow(new AuthException("User is already an admin", "ALREADY_ADMIN", HttpStatus.BAD_REQUEST));

        mockMvc.perform(post("/api/super-admin/users/2/promote")
                        .with(authentication(superAdminToken)))
                .andExpect(status().isBadRequest());
    }

    // ─────────────────────────────────────────────────────────────
    // POST /api/super-admin/admins/{id}/demote
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldDemoteAdminToCustomer() throws Exception {
        User demoted = User.builder()
                .id(2L).email("admin@test.com")
                .firstName("Admin").lastName("User")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true).isDeleted(false)
                .build();

        when(adminService.demoteToCustomer(2L)).thenReturn(demoted);

        mockMvc.perform(post("/api/super-admin/admins/2/demote")
                        .with(authentication(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userRole").value("CUSTOMER"));

        verify(adminService).demoteToCustomer(2L);
    }

    @Test
    void shouldReturn403WhenDemotingSuperAdminAccount() throws Exception {
        when(adminService.demoteToCustomer(1L))
                .thenThrow(new AuthException(
                        "Super admin accounts cannot be demoted via this endpoint",
                        "FORBIDDEN",
                        HttpStatus.FORBIDDEN));

        mockMvc.perform(post("/api/super-admin/admins/1/demote")
                        .with(authentication(superAdminToken)))
                .andExpect(status().isForbidden());
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/super-admin/admins
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnAllAdmins() throws Exception {
        when(adminService.getAllAdmins()).thenReturn(List.of(adminUser));

        mockMvc.perform(get("/api/super-admin/admins")
                        .with(authentication(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("admin@test.com"))
                .andExpect(jsonPath("$[0].userRole").value("ADMIN"));

        verify(adminService).getAllAdmins();
    }

    @Test
    void shouldReturn403WhenNonSuperAdminListsAdmins() throws Exception {
        mockMvc.perform(get("/api/super-admin/admins")
                        .with(authentication(adminToken)))
                .andExpect(status().isForbidden());

        verify(adminService, never()).getAllAdmins();
    }

    // ─────────────────────────────────────────────────────────────
    // GET /api/super-admin/super-admins
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnAllSuperAdmins() throws Exception {
        when(adminService.getAllSuperAdmins()).thenReturn(List.of(superAdminUser));

        mockMvc.perform(get("/api/super-admin/super-admins")
                        .with(authentication(superAdminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].email").value("superadmin@test.com"))
                .andExpect(jsonPath("$[0].userRole").value("SUPER_ADMIN"));

        verify(adminService).getAllSuperAdmins();
    }
}
