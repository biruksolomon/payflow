package com.payflow.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic single-message response used for simple acknowledgement endpoints
 * (logout, delete, mark-as-read, etc.).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MessageResponse {

    private String message;

    public static MessageResponse of(String message) {
        return new MessageResponse(message);
    }
}
