package com.payflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.config.TestSecurityConfig;
import com.payflow.backend.dto.AuthRequest;
import com.payflow.backend.dto.AuthResponse;
import com.payflow.backend.dto.RegisterRequest;
import com.payflow.backend.security.CustomUserDetailsService;
import com.payflow.backend.security.JwtAuthenticationFilter;
import com.payflow.backend.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.SecurityFilterAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;

import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


@WebMvcTest(
        controllers = AuthController.class,
        excludeAutoConfiguration = {
                SecurityAutoConfiguration.class,
                SecurityFilterAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)

class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    private AuthResponse authResponse;

    @BeforeEach
    void setUp() {
        AuthResponse.UserAuthData user =
                AuthResponse.UserAuthData.builder()
                        .id(1L)
                        .email("user@test.com")
                        .firstName("John")
                        .lastName("Doe")
                        .role("CUSTOMER")
                        .emailVerified(true)
                        .build();

        authResponse =
                AuthResponse.builder()
                        .accessToken("access-token")
                        .refreshToken("refresh-token")
                        .tokenType("Bearer")
                        .expiresIn(86400L)
                        .user(user)
                        .build();
    }

    @Test
    void shouldRegisterSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("Password123!");
        request.setPasswordConfirm("Password123!");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(authService.register(any())).thenReturn(authResponse);

        mockMvc.perform(
                        post("/api/auth/register")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").value("access-token"));

        verify(authService).register(any());
    }

    @Test
    void shouldLoginSuccessfully() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("user@test.com");
        request.setPassword("Password123!");

        when(authService.login(any())).thenReturn(authResponse);

        mockMvc.perform(
                        post("/api/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(objectMapper.writeValueAsString(request))
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token"));

        verify(authService).login(any());
    }

    @Test
    void shouldVerifyEmailSuccessfully() throws Exception {
        mockMvc.perform(
                        post("/api/auth/verify-email")
                                .param("email", "user@test.com")
                                .param("token", "token123")
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully"));

        verify(authService).verifyEmail("user@test.com", "token123");
    }

    @Test
    void shouldResendVerificationSuccessfully() throws Exception {
        mockMvc.perform(
                        post("/api/auth/resend-verification")
                                .param("email", "user@test.com")
                )
                .andExpect(status().isOk());

        verify(authService).resendVerificationEmail("user@test.com");
    }

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        when(authService.refreshToken("refresh-token")).thenReturn(authResponse);

        mockMvc.perform(
                        post("/api/auth/refresh-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {
                                          "refresh_token":"refresh-token"
                                        }
                                        """)
                )
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token"));
    }

    @Test
    void shouldReturnBadRequestWhenRefreshTokenMissing() throws Exception {
        mockMvc.perform(
                        post("/api/auth/refresh-token")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}")
                )
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldLogoutSuccessfully() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));
    }
}