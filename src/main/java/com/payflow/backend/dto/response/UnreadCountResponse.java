package com.payflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response DTO for GET /api/notifications/unread/count.
 * Replaces the ad-hoc {@code Map<String, Long>} used previously.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UnreadCountResponse {

    private Long unreadCount;

    public static UnreadCountResponse of(long count) {
        return new UnreadCountResponse(count);
    }
}
