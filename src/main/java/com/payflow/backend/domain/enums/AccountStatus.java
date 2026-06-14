package com.payflow.backend.domain.enums;


/**
 * User Account Status Enumeration
 */
public enum AccountStatus {
    ACTIVE("Active"),
    SUSPENDED("Suspended"),
    DELETED("Deleted");

    private final String displayName;

    AccountStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
