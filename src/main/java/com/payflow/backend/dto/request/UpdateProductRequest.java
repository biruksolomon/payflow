package com.payflow.backend.dto.request;

import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Request body for PUT /api/products/{id} (admin only).
 * All fields are optional — only non-null values are applied to the entity.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateProductRequest {

    @Size(max = 255, message = "Product name must be at most 255 characters")
    private String name;

    private String description;

    @Size(max = 100, message = "Category must be at most 100 characters")
    private String category;

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

    private Boolean isFeatured;

    private Boolean isActive;
}
