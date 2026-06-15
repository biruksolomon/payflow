package com.payflow.backend.security;

import com.payflow.backend.domain.entity.User;
import com.payflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CustomUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        try {
            User user = userRepository.findActiveByEmail(email)
                    .orElseThrow(() -> {
                        log.warn("[CustomUserDetailsService] User not found with email: {}", email);
                        return new UsernameNotFoundException("User not found with email: " + email);
                    });

            log.debug("[CustomUserDetailsService] Loading user details for email: {}", email);
            return new PayFlowUserDetails(user);
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("[CustomUserDetailsService] Error loading user details: {}", e.getMessage());
            throw new UsernameNotFoundException("Error loading user details", e);
        }
    }

    public UserDetails loadUserById(Long userId) throws UsernameNotFoundException {
        try {
            User user = userRepository.findActiveById(userId)
                    .orElseThrow(() -> {
                        log.warn("[CustomUserDetailsService] User not found with id: {}", userId);
                        return new UsernameNotFoundException("User not found with id: " + userId);
                    });

            log.debug("[CustomUserDetailsService] Loading user details for id: {}", userId);
            return new PayFlowUserDetails(user);
        } catch (UsernameNotFoundException e) {
            throw e;
        } catch (Exception e) {
            log.error("[CustomUserDetailsService] Error loading user details: {}", e.getMessage());
            throw new UsernameNotFoundException("Error loading user details", e);
        }
    }
}

