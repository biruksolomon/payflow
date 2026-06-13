package com.payflow.backend.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

// ==================== PRODUCT ENTITY ====================
@Entity
@Table(name = "products", indexes = {
        @Index(name = "idx_products_category", columnList = "category"),
        @Index(name = "idx_products_is_active", columnList = "is_active"),
        @Index(name = "idx_products_sku", columnList = "sku"),
        @Index(name = "idx_products_created_at", columnList = "created_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Product Info
    @Column(unique = true, nullable = false, length = 100)
    private String sku;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, length = 100)
    private String category;

    // Pricing
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal price;

    @Column(precision = 19, scale = 2)
    private BigDecimal discountPrice;

    @Column(length = 3)
    @Builder.Default
    private String currency = "USD";

    // Inventory
    @Column(nullable = false)
    @Builder.Default
    private Integer quantityInStock = 0;

    @Column()
    @Builder.Default
    private Integer reservedQuantity = 0;

    @Column()
    @Builder.Default
    private Integer lowStockThreshold = 10;

    // Media
    @Column(length = 500)
    private String imageUrl;

    @Column(length = 500)
    private String thumbnailUrl;

    // Status & Metadata
    @Column(nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @Column(nullable = false)
    @Builder.Default
    private Boolean isFeatured = false;

    @Column(precision = 3, scale = 2)
    @Builder.Default
    private BigDecimal rating = BigDecimal.ZERO;

    @Column()
    @Builder.Default
    private Integer reviewCount = 0;

    // Tracking
    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    // SEO
    @Column(columnDefinition = "TEXT")
    private String searchKeywords;

    // Helper methods
    public Integer getAvailableQuantity() {
        return quantityInStock - (reservedQuantity != null ? reservedQuantity : 0);
    }

    public boolean isInStock() {
        return getAvailableQuantity() > 0;
    }

    public boolean isLowStock() {
        return getAvailableQuantity() <= lowStockThreshold;
    }

    public BigDecimal getEffectivePrice() {
        if (discountPrice != null && discountPrice.compareTo(BigDecimal.ZERO) > 0) {
            return discountPrice;
        }
        return price;
    }

    public BigDecimal getDiscountPercentage() {

        if (price == null || price.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO;
        }

        if (discountPrice != null &&
                discountPrice.compareTo(BigDecimal.ZERO) > 0) {

            return price.subtract(discountPrice)
                    .divide(price, 2, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));
        }

        return BigDecimal.ZERO;
    }
}
