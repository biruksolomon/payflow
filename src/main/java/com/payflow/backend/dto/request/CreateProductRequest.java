package com.payflow.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for POST /api/products (admin only).
 * Replaces the raw {@code Map<String, Object>} used previously.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProductRequest {

    @NotBlank(message = "SKU is required")
    @Size(max = 100, message = "SKU must be at most 100 characters")
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(max = 255, message = "Product name must be at most 255 characters")
    private String name;

    private String description;

    @NotBlank(message = "Category is required")
    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than 0")
    @Digits(integer = 17, fraction = 2, message = "Price must have at most 2 decimal places")
    private BigDecimal price;

    @DecimalMin(value = "0.01", message = "Discount price must be greater than 0")
    @Digits(integer = 17, fraction = 2, message = "Discount price must have at most 2 decimal places")
    private BigDecimal discountPrice;

    @Min(value = 0, message = "Quantity in stock must be 0 or more")
    private Integer quantityInStock;

    @Min(value = 1, message = "Low stock threshold must be at least 1")
    private Integer lowStockThreshold;

    @Size(max = 500, message = "Image URL must be at most 500 characters")
    private String imageUrl;

    @Size(max = 500, message = "Thumbnail URL must be at most 500 characters")
    private String thumbnailUrl;

    @Builder.Default
    private Boolean isFeatured = false;
}
