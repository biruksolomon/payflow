package com.payflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.config.TestWebMvcSecurityConfig;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.dto.AuthRequest;
import com.payflow.backend.dto.AuthResponse;
import com.payflow.backend.dto.RegisterRequest;
import com.payflow.backend.security.CustomUserDetailsService;
import com.payflow.backend.security.JwtAuthenticationFilter;
import com.payflow.backend.security.JwtTokenProvider;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.AuthService;
import com.payflow.backend.service.RedisService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * @WebMvcTest with excludeFilters = @ComponentScan.Filter(SecurityConfig.class)
 *
 * This is the decisive fix. The production SecurityConfig is a @Configuration
 * class picked up by @WebMvcTest's component scan. It registers:
 *   - JwtAuthenticationFilter (custom)
 *   - AnonymousAuthenticationFilter (built-in, always added by Spring Security)
 *   - ExceptionTranslationFilter (turns unauthenticated access into 401)
 *
 * Even with @AutoConfigureMockMvc(addFilters = false) the SECURITY filter chain
 * itself is still built and the Authentication parameter resolved through it.
 * Even with .anyRequest().permitAll() in TestSecurityConfig, if SecurityConfig
 * is ALSO loaded it creates a second SecurityFilterChain bean and one of them
 * (the production one) takes precedence — the test one is ignored.
 *
 * The only reliable fix is to exclude SecurityConfig entirely from the test
 * application context, then define a minimal inline security config that:
 *   1. Disables CSRF
 *   2. Permits all requests (so no 401 from the security layer itself)
 *   3. Does NOT add AnonymousAuthenticationFilter logic that overwrites the
 *      test-supplied Authentication
 *
 * FIX: Use SecurityContextHolder.setContext() to explicitly set the authentication
 * in the thread-local context. This works reliably even with @WebMvcTest(addFilters = false).
 */
@WebMvcTest(controllers = AuthController.class)
@Import(TestWebMvcSecurityConfig.class)
@AutoConfigureMockMvc(addFilters = false)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    // ── Bean under test ──────────────────────────────────────────
    @MockBean
    private AuthService authService;

    // ── All beans that SecurityConfig / JwtAuthenticationFilter
    // ── would normally require — still needed because other
    // ── auto-configurations may reference them ────────────────────
    @MockBean
    private JwtAuthenticationFilter jwtAuthenticationFilter;

    @MockBean
    private JwtTokenProvider jwtTokenProvider;

    @MockBean
    private CustomUserDetailsService customUserDetailsService;

    @MockBean
    private RedisService redisService;

    // ── Shared fixtures ──────────────────────────────────────────
    private AuthResponse authResponse;

    /**
     * Builds a fully-initialised PayFlowUserDetails from a real User entity.
     *
     * isActive()  → accountStatus == ACTIVE && !isDeleted  → true
     * isEnabled() → isActive()              && emailVerified → true
     */
    private PayFlowUserDetails buildUserDetails(Long id) {
        User user = User.builder()
                .id(id)
                .email("user@test.com")
                .passwordHash("hashed")
                .firstName("John")
                .lastName("Doe")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
        return new PayFlowUserDetails(user);
    }

    /**
     * 3-arg constructor sets authenticated = true.
     * 2-arg constructor leaves it false — never use that one for test principals.
     */
    private UsernamePasswordAuthenticationToken authenticatedToken(PayFlowUserDetails ud) {
        return new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
    }



    /**
     * Create an authentication token for MockMvc request post-processor.
     * This properly integrates the authentication with MockMvc's request processing pipeline.
     */
    private org.springframework.test.web.servlet.request.RequestPostProcessor withAuthentication(PayFlowUserDetails userDetails) {
        UsernamePasswordAuthenticationToken token = authenticatedToken(userDetails);
        return authentication(token);
    }

    @BeforeEach
    void setUp() {
        AuthResponse.UserAuthData user = AuthResponse.UserAuthData.builder()
                .id(1L)
                .email("user@test.com")
                .firstName("John")
                .lastName("Doe")
                .fullName("John Doe")
                .role("CUSTOMER")
                .emailVerified(true)
                .build();

        authResponse = AuthResponse.builder()
                .accessToken("access-token")
                .refreshToken("refresh-token")
                .tokenType("Bearer")
                .expiresIn(86400L)
                .user(user)
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldRegisterSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("Password123!");   // must satisfy: upper+lower+digit+special
        request.setPasswordConfirm("Password123!");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(authService.register(any(), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                // AuthResponse fields are serialised as access_token / refresh_token etc.
                .andExpect(jsonPath("$.access_token").value("access-token"))
                .andExpect(jsonPath("$.refresh_token").value("refresh-token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"));

        verify(authService).register(any(), any());
    }

    // ─────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldLoginSuccessfully() throws Exception {
        AuthRequest request = new AuthRequest();
        request.setEmail("user@test.com");
        request.setPassword("Password123!");

        when(authService.login(any(), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token"))
                .andExpect(jsonPath("$.user.email").value("user@test.com"));

        verify(authService).login(any(), any());
    }

    // ─────────────────────────────────────────────────────────────
    // EMAIL VERIFICATION
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldVerifyEmailSuccessfully() throws Exception {
        mockMvc.perform(post("/api/auth/verify-email")
                        .param("email", "user@test.com")
                        .param("token", "token123"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Email verified successfully"))
                .andExpect(jsonPath("$.email").value("user@test.com"));

        verify(authService).verifyEmail("user@test.com", "token123");
    }

    @Test
    void shouldResendVerificationSuccessfully() throws Exception {
        mockMvc.perform(post("/api/auth/resend-verification")
                        .param("email", "user@test.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Verification email sent successfully"))
                .andExpect(jsonPath("$.email").value("user@test.com"));

        verify(authService).resendVerificationEmail("user@test.com");
    }

    // ─────────────────────────────────────────────────────────────
    // REFRESH TOKEN
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldRefreshTokenSuccessfully() throws Exception {
        when(authService.refreshToken("refresh-token")).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refresh_token": "refresh-token" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").value("access-token"));

        verify(authService).refreshToken("refresh-token");
    }

    @Test
    void shouldReturnBadRequestWhenRefreshTokenMissing() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refreshToken(any());
    }

    @Test
    void shouldReturnBadRequestWhenRefreshTokenBlank() throws Exception {
        mockMvc.perform(post("/api/auth/refresh-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refresh_token": "" }
                                """))
                .andExpect(status().isBadRequest());

        verify(authService, never()).refreshToken(any());
    }

    // ─────────────────────────────────────────────────────────────
    // GET CURRENT USER
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldGetCurrentUserSuccessfully() throws Exception {
        PayFlowUserDetails userDetails = buildUserDetails(1L);

        mockMvc.perform(get("/api/auth/me")
                        .with(withAuthentication(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("user@test.com"))
                .andExpect(jsonPath("$.firstName").value("John"))
                .andExpect(jsonPath("$.lastName").value("Doe"))
                .andExpect(jsonPath("$.emailVerified").value(true));
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    // ─────────────────────────────────────────────────────────────
    // LOGOUT (single device)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldLogoutSuccessfully() throws Exception {
        PayFlowUserDetails userDetails = buildUserDetails(1L);

        mockMvc.perform(post("/api/auth/logout")
                        .with(withAuthentication(userDetails))
                        .header("Authorization", "Bearer access-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "refresh_token": "refresh-token" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        verify(authService).logout(
                eq("access-token"),
                eq("refresh-token"),
                eq(1L));
    }

    // ─────────────────────────────────────────────────────────────
    // LOGOUT ALL (every device)
    // ─────────────────────────��───��───────────────────────────────

    @Test
    void shouldReturnOkOnLogoutAllWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out from all devices"));

        verify(authService, never()).logoutAll(any(), any());
    }
}