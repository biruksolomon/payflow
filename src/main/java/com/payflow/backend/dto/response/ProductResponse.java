package com.payflow.backend.dto.response;

import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.domain.enums.Currency;
import lombok.Builder;
import lombok.Value;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Safe, flat DTO for Product — no Hibernate proxy references.
 * Maps all scalar fields from the Product entity; createdBy is
 * represented as a plain Long (createdByUserId) to avoid lazy-load issues.
 */
@Value
@Builder
public class ProductResponse {

    Long id;
    String sku;
    String name;
    String description;
    String category;
    BigDecimal price;
    BigDecimal discountPrice;
    BigDecimal effectivePrice;
    BigDecimal discountPercentage;
    Currency currency;
    Integer quantityInStock;
    Integer reservedQuantity;
    Integer availableQuantity;
    Integer lowStockThreshold;
    Boolean isActive;
    Boolean isFeatured;
    Boolean inStock;
    Boolean lowStock;
    BigDecimal rating;
    Integer reviewCount;
    String imageUrl;
    String thumbnailUrl;
    String searchKeywords;
    Long createdByUserId;
    LocalDateTime createdAt;
    LocalDateTime updatedAt;

    /**
     * Factory method — converts a managed (or detached) Product entity to this
     * DTO without touching any lazy-loaded association.
     */
    public static ProductResponse from(Product p) {
        return ProductResponse.builder()
                .id(p.getId())
                .sku(p.getSku())
                .name(p.getName())
                .description(p.getDescription())
                .category(p.getCategory())
                .price(p.getPrice())
                .discountPrice(p.getDiscountPrice())
                .effectivePrice(p.getEffectivePrice())
                .discountPercentage(p.getDiscountPercentage())
                .currency(p.getCurrency())
                .quantityInStock(p.getQuantityInStock())
                .reservedQuantity(p.getReservedQuantity())
                .availableQuantity(p.getAvailableQuantity())
                .lowStockThreshold(p.getLowStockThreshold())
                .isActive(p.getIsActive())
                .isFeatured(p.getIsFeatured())
                .inStock(p.isInStock())
                .lowStock(p.isLowStock())
                .rating(p.getRating())
                .reviewCount(p.getReviewCount())
                .imageUrl(p.getImageUrl())
                .thumbnailUrl(p.getThumbnailUrl())
                .searchKeywords(p.getSearchKeywords())
                // Access the FK id directly without initialising the proxy
                .createdByUserId(p.getCreatedBy() != null ? p.getCreatedBy().getId() : null)
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}