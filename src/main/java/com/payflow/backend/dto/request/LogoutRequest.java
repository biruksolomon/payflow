package com.payflow.backend.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Optional request body for POST /api/auth/logout.
 * The refresh_token field is optional; when supplied it is revoked in addition
 * to blacklisting the current access token.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogoutRequest {

    @JsonProperty("refresh_token")
    private String refreshToken;
}
