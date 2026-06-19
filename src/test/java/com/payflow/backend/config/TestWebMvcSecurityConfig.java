package com.payflow.backend.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

// Minimal security filter chain for @WebMvcTest slices.
//
// SecurityConfig is annotated @ConditionalOnMissingBean(SecurityFilterChain.class),
// so importing this class causes Spring to use this bean instead of the production
// one.  The production SecurityFilterChain would require a real JwtAuthenticationFilter
// (and therefore real JwtTokenProvider / CustomUserDetailsService / RedisService wired
// together), which is not what we want in a @WebMvcTest slice.
//
// What this config does:
//  - Disables CSRF: not relevant for stateless REST endpoints under test.
//  - Permits all requests: authorization is NOT what AuthControllerTest exercises.
//    The controller methods themselves decide what to return when Authentication is
//    null (e.g. /me returns 401, /logout returns 200 regardless).  Allowing every
//    request through means those controller-level decisions are what the assertions
//    see, not an earlier 401 from the security layer.
//  - Disables anonymous(): CRITICAL — without this, unauthenticated requests receive
//    an AnonymousAuthenticationToken whose isAuthenticated() returns true.  The
//    controller checks (authentication == null || !isAuthenticated()) to return 401
//    for /me.  With AnonymousAuthenticationToken that check evaluates to false and
//    the controller tries to cast the anonymous principal to PayFlowUserDetails,
//    causing a ClassCastException instead of 401.  Disabling the anonymous filter
//    ensures unauthenticated requests deliver null Authentication to the controller,
//    which is exactly what shouldReturn401WhenNotAuthenticated expects.
//
// Why addFilters = false was removed from the test class:
//  SecurityMockMvcRequestPostProcessors.authentication(token) works via
//  SecurityMockMvcConfigurer, which is itself registered as a filter.
//  addFilters = false removes ALL filters including SecurityMockMvcConfigurer,
//  so .with(authentication(...)) can never set the SecurityContext and the
//  Authentication always arrives as null inside the controller.  The fix is to
//  keep filters enabled and let this permissive SecurityFilterChain handle
//  authorization instead.
@TestConfiguration
public class TestWebMvcSecurityConfig {

    @Bean
    public SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                // Disable the anonymous filter so that unauthenticated requests
                // deliver null Authentication to controller methods instead of an
                // AnonymousAuthenticationToken (which has isAuthenticated() == true).
                .anonymous(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }
}
