package com.payflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for POST /api/notifications/read-all.
 * Replaces the ad-hoc {@code Map<String, Object>} used previously.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MarkAllReadResponse {

    private String message;
    private Integer updatedCount;

    public static MarkAllReadResponse of(int updatedCount) {
        return new MarkAllReadResponse("All notifications marked as read", updatedCount);
    }
}
