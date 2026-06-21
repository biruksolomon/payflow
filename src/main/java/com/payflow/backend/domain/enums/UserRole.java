package com.payflow.backend.domain.enums;

import lombok.Getter;

@Getter
public enum UserRole {
    CUSTOMER("Customer"),
    ADMIN("Administrator"),
    SUPER_ADMIN("Super Administrator");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public static UserRole fromString(String value) {
        for (UserRole role : UserRole.values()) {
            if (role.name().equalsIgnoreCase(value)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Invalid user role: " + value);
    }
}
