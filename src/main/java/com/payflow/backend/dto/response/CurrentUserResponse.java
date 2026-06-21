package com.payflow.backend.dto.response;

import com.payflow.backend.security.PayFlowUserDetails;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for the GET /api/auth/me endpoint.
 * Replaces the ad-hoc {@code Map<String, Object>} that was previously built inline.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CurrentUserResponse {

    private Long id;
    private String email;
    private String firstName;
    private String lastName;
    private String fullName;
    private String role;
    private Boolean emailVerified;
    private Boolean accountActive;

    public static CurrentUserResponse from(PayFlowUserDetails userDetails) {
        String role = userDetails.getAuthorities().stream()
                .findFirst()
                .map(a -> a.getAuthority().replace("ROLE_", ""))
                .orElse("CUSTOMER");

        return CurrentUserResponse.builder()
                .id(userDetails.getId())
                .email(userDetails.getUsername())
                .firstName(userDetails.getFirstName())
                .lastName(userDetails.getLastName())
                .fullName(userDetails.getFirstName() + " " + userDetails.getLastName())
                .role(role)
                .emailVerified(userDetails.isEmailVerified())
                .accountActive(userDetails.isEnabled())
                .build();
    }
}
