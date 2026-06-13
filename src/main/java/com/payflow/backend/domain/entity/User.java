package com.payflow.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "users", indexes = {
        @Index(name = "idx_users_email", columnList = "email"),
        @Index(name = "idx_users_user_role", columnList = "user_role"),
        @Index(name = "idx_users_account_status", columnList = "account_status"),
        @Index(name = "idx_users_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Authentication & Identity
    @Column(unique = true, nullable = false, length = 255)
    private String email;

    @Column(nullable = false, length = 255)
    private String passwordHash;

    @Column(nullable = false, length = 100)
    private String firstName;

    @Column(nullable = false, length = 100)
    private String lastName;

    @Column(length = 20)
    private String phone;

    // Profile & Status
    @Column(nullable = false, length = 50)
    @Builder.Default
    private String userRole = "CUSTOMER"; // CUSTOMER, ADMIN

    @Column(nullable = false, length = 50)
    @Builder.Default
    private String accountStatus = "ACTIVE"; // ACTIVE, SUSPENDED, DELETED

    @Column(nullable = false)
    @Builder.Default
    private Boolean emailVerified = false;

    @Column(length = 255)
    private String verificationToken;

    private LocalDateTime verificationTokenExpiry;

    // Address Information
    @Column(length = 255)
    private String streetAddress;

    @Column(length = 100)
    private String city;

    @Column(length = 100)
    private String stateProvince;

    @Column(length = 20)
    private String postalCode;

    @Column(length = 100)
    private String country;

    // Account Info
    @Column(length = 50)
    private String preferredPaymentMethod; // STRIPE, PAYPAL

    @Column(length = 3)
    @Builder.Default
    private String preferredCurrency = "USD";

    // Tracking
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime lastLogin;

    // Soft Delete Support
    @Column(nullable = false)
    @Builder.Default
    private Boolean isDeleted = false;

    private LocalDateTime deletedAt;

    // Helper methods
    public String getFullName() {
        return firstName + " " + lastName;
    }

    public boolean isActive() {
        return "ACTIVE".equals(accountStatus) && !isDeleted;
    }

    public boolean isAdmin() {
        return "ADMIN".equals(userRole);
    }
}