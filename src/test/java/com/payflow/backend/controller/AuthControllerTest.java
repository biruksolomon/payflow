package com.payflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.config.SecurityConfig;
import com.payflow.backend.config.TestWebMvcSecurityConfig;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.dto.request.AuthRequest;
import com.payflow.backend.dto.response.AuthResponse;
import com.payflow.backend.dto.request.RegisterRequest;
import com.payflow.backend.security.JwtAuthenticationFilter;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.AuthService;
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

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// ─────────────────────────────────────────────────────────────────────────────
// Why SecurityConfig and JwtAuthenticationFilter are excluded:
//
//  @WebMvcTest scans @Configuration and @Component classes in the web slice.
//  SecurityConfig is @EnableWebSecurity @Configuration — it registers a
//  SecurityFilterChain that wires JwtAuthenticationFilter (a @Component) into
//  the filter chain via .addFilterBefore(...).  JwtAuthenticationFilter is a
//  Mockito mock whose doFilter() method is a no-op void stub: it swallows every
//  request and never calls chain.doFilter(), so DispatcherServlet never runs,
//  Handler: Type = null, and all tests get an empty-body 200.
//
//  Excluding SecurityConfig removes the production SecurityFilterChain entirely.
//  Excluding JwtAuthenticationFilter prevents it from being registered as a
//  @Component bean in the slice, eliminating all its transitive dependencies
//  (JwtTokenProvider, CustomUserDetailsService, RedisService).
//  TestWebMvcSecurityConfig (imported below) becomes the sole security config:
//  it disables CSRF, permits all requests, and disables anonymous() so that
//  unauthenticated requests deliver null Authentication to controller methods.
//
// Why .with(authentication(...)) works now:
//
//  @WebMvcTest auto-imports SecurityMockMvcAutoConfiguration which applies the
//  SecurityMockMvcConfigurer (springSecurity()) to MockMvc.  That configurer is
//  what makes .with(authentication(...)) write the Authentication into the
//  SecurityContext before DispatcherServlet resolves the method argument.
//  Previously @AutoConfigureMockMvc was incorrectly added, creating a second
//  MockMvc bean with no handler mappings.  Without it, @WebMvcTest's own MockMvc
//  bean is the sole candidate and has AuthController correctly mapped.
//
// Why anonymous() is disabled in TestWebMvcSecurityConfig:
//
//  Without it, Spring's AnonymousAuthenticationFilter injects an
//  AnonymousAuthenticationToken (isAuthenticated() == true) for unauthenticated
//  requests.  The controller guard (authentication == null || !isAuthenticated())
//  would never trigger 401.  Disabling anonymous() ensures unauthenticated
//  requests deliver null Authentication so shouldReturn401WhenNotAuthenticated passes.
// ─────────────────────────────────────────────────────────────────────────────
@WebMvcTest(
        controllers = AuthController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestWebMvcSecurityConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AuthService authService;

    // ── Shared response fixture ───────────────────────────────────────────────
    private AuthResponse authResponse;

    // ── Fixture helpers ───────────────────────────────────────────────────────

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

    // 3-arg UsernamePasswordAuthenticationToken marks the principal as authenticated.
    // The 2-arg constructor leaves isAuthenticated() == false, which the controller's
    // (authentication == null || !isAuthenticated()) guard treats the same as null.
    private UsernamePasswordAuthenticationToken authenticatedToken(PayFlowUserDetails ud) {
        return new UsernamePasswordAuthenticationToken(ud, null, ud.getAuthorities());
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

    // ─────────────────────────────────────────────────────────────────────────
    // REGISTER
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldRegisterSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setEmail("user@test.com");
        request.setPassword("Password123!");
        request.setPasswordConfirm("Password123!");
        request.setFirstName("John");
        request.setLastName("Doe");

        when(authService.register(any(), any())).thenReturn(authResponse);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.access_token").value("access-token"))
                .andExpect(jsonPath("$.refresh_token").value("refresh-token"))
                .andExpect(jsonPath("$.token_type").value("Bearer"));

        verify(authService).register(any(), any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOGIN
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // EMAIL VERIFICATION
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // REFRESH TOKEN
    // ─────────────────────────────────────────────────────────────────────────

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

    // ─────────────────────────────────────────────────────────────────────────
    // GET CURRENT USER
    // .with(authentication(...)) injects the Authentication inside MockMvc.perform()
    // after MockMvc resets SecurityContextHolder but before DispatcherServlet
    // resolves the Authentication method argument.
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldGetCurrentUserSuccessfully() throws Exception {
        PayFlowUserDetails userDetails = buildUserDetails(1L);

        mockMvc.perform(get("/api/auth/me")
                        .with(authentication(authenticatedToken(userDetails))))
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

    // ─────────────────────────────────────────────────────────────────────────
    // LOGOUT (current device)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldLogoutSuccessfully() throws Exception {
        PayFlowUserDetails userDetails = buildUserDetails(1L);

        mockMvc.perform(post("/api/auth/logout")
                        .with(authentication(authenticatedToken(userDetails)))
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

    @Test
    void shouldLogoutWithoutRefreshTokenBody() throws Exception {
        PayFlowUserDetails userDetails = buildUserDetails(1L);

        mockMvc.perform(post("/api/auth/logout")
                        .with(authentication(authenticatedToken(userDetails)))
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        verify(authService).logout(
                eq("access-token"),
                isNull(),
                eq(1L));
    }

    @Test
    void shouldReturnOkOnLogoutWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/auth/logout")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logout successful"));

        verify(authService, never()).logout(any(), any(), any());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LOGOUT ALL (every device)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    void shouldLogoutAllSuccessfully() throws Exception {
        PayFlowUserDetails userDetails = buildUserDetails(1L);

        mockMvc.perform(post("/api/auth/logout-all")
                        .with(authentication(authenticatedToken(userDetails)))
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out from all devices"));

        verify(authService).logoutAll(eq("access-token"), eq(1L));
    }

    @Test
    void shouldReturnOkOnLogoutAllWhenNotAuthenticated() throws Exception {
        mockMvc.perform(post("/api/auth/logout-all")
                        .header("Authorization", "Bearer access-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Logged out from all devices"));

        verify(authService, never()).logoutAll(any(), any());
    }
}
