package com.payflow.backend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal security filter chain for @WebMvcTest slices.
 *
 * Why this shape:
 *  - The security filter chain MUST be active (the test no longer uses
 *    addFilters = false) so that Spring Security's test support
 *    (SecurityMockMvcRequestPostProcessors.authentication(...)) can flow the
 *    supplied Authentication through SecurityContextHolderFilter into the
 *    SecurityContextHolder, and SecurityContextHolderAwareRequestFilter can
 *    expose it via request.getUserPrincipal() — which is how the controller's
 *    `Authentication` method parameter is resolved.
 *
 *  - permitAll(): authorization is NOT what we are testing here. The controller
 *    methods themselves decide what to do when Authentication is null
 *    (e.g. /me returns 401, /logout returns 200). Letting every request through
 *    the filter chain means those controller-level decisions are exercised
 *    instead of being short-circuited by a 401 from the authorization filter.
 *
 *  - anonymous() disabled: without this, unauthenticated requests would receive
 *    an AnonymousAuthenticationToken (principal = "anonymousUser") instead of a
 *    null Authentication. The controller casts the principal to
 *    PayFlowUserDetails, so an anonymous token would cause a ClassCastException
 *    (HTTP 500) rather than the expected null-Authentication branch.
 *
 *  - We deliberately do NOT override the SecurityContextRepository. The default
 *    repository is what the .with(authentication(...)) post-processor writes to,
 *    so overriding it (as the previous version did) broke that flow.
 */
@TestConfiguration
public class TestWebMvcSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
