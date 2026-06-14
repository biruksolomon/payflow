package com.payflow.backend.entity;


import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.domain.enums.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class ProductTest {

    @Test
    @DisplayName("Should calculate available quantity correctly")
    void shouldCalculateAvailableQuantity() {

        Product product = Product.builder()
                .quantityInStock(100)
                .reservedQuantity(20)
                .build();

        assertEquals(
                80,
                product.getAvailableQuantity()
        );
    }

    @Test
    @DisplayName("Should treat null reserved quantity as zero")
    void shouldHandleNullReservedQuantity() {

        Product product = Product.builder()
                .quantityInStock(100)
                .reservedQuantity(null)
                .build();

        assertEquals(
                100,
                product.getAvailableQuantity()
        );
    }

    @Test
    @DisplayName("Should return true when product is in stock")
    void shouldReturnTrueWhenInStock() {

        Product product = Product.builder()
                .quantityInStock(50)
                .reservedQuantity(10)
                .build();

        assertTrue(product.isInStock());
    }

    @Test
    @DisplayName("Should return false when product is out of stock")
    void shouldReturnFalseWhenOutOfStock() {

        Product product = Product.builder()
                .quantityInStock(10)
                .reservedQuantity(10)
                .build();

        assertFalse(product.isInStock());
    }

    @Test
    @DisplayName("Should identify low stock correctly")
    void shouldIdentifyLowStock() {

        Product product = Product.builder()
                .quantityInStock(15)
                .reservedQuantity(10)
                .lowStockThreshold(10)
                .build();

        assertTrue(product.isLowStock());
    }

    @Test
    @DisplayName("Should return false when stock is above threshold")
    void shouldNotBeLowStock() {

        Product product = Product.builder()
                .quantityInStock(100)
                .reservedQuantity(10)
                .lowStockThreshold(20)
                .build();

        assertFalse(product.isLowStock());
    }

    @Test
    @DisplayName("Should return regular price when no discount exists")
    void shouldReturnRegularPrice() {

        Product product = Product.builder()
                .price(BigDecimal.valueOf(100))
                .discountPrice(null)
                .build();

        assertEquals(
                BigDecimal.valueOf(100),
                product.getEffectivePrice()
        );
    }

    @Test
    @DisplayName("Should return discount price when available")
    void shouldReturnDiscountPrice() {

        Product product = Product.builder()
                .price(BigDecimal.valueOf(100))
                .discountPrice(BigDecimal.valueOf(80))
                .build();

        assertEquals(
                BigDecimal.valueOf(80),
                product.getEffectivePrice()
        );
    }

    @Test
    @DisplayName("Should ignore zero discount price")
    void shouldIgnoreZeroDiscountPrice() {

        Product product = Product.builder()
                .price(BigDecimal.valueOf(100))
                .discountPrice(BigDecimal.ZERO)
                .build();

        assertEquals(
                BigDecimal.valueOf(100),
                product.getEffectivePrice()
        );
    }

    @Test
    @DisplayName("Should calculate discount percentage correctly")
    void shouldCalculateDiscountPercentage() {

        Product product = Product.builder()
                .price(BigDecimal.valueOf(100))
                .discountPrice(BigDecimal.valueOf(80))
                .build();

        assertEquals(
                BigDecimal.valueOf(20.00).setScale(2),
                product.getDiscountPercentage()
        );
    }

    @Test
    @DisplayName("Should return zero discount percentage when no discount exists")
    void shouldReturnZeroDiscountPercentage() {

        Product product = Product.builder()
                .price(BigDecimal.valueOf(100))
                .discountPrice(null)
                .build();

        assertEquals(
                BigDecimal.ZERO,
                product.getDiscountPercentage()
        );
    }

    @Test
    @DisplayName("Should apply builder default values")
    void shouldApplyBuilderDefaults() {

        Product product = Product.builder()
                .sku("SKU-001")
                .name("Laptop")
                .category("Electronics")
                .price(BigDecimal.valueOf(1000))
                .build();

        assertEquals(Currency.USD, product.getCurrency());

        assertEquals(
                Integer.valueOf(0),
                product.getQuantityInStock()
        );

        assertEquals(
                Integer.valueOf(0),
                product.getReservedQuantity()
        );

        assertEquals(
                Integer.valueOf(10),
                product.getLowStockThreshold()
        );

        assertTrue(product.getIsActive());

        assertFalse(product.getIsFeatured());

        assertEquals(
                BigDecimal.ZERO,
                product.getRating()
        );

        assertEquals(
                Integer.valueOf(0),
                product.getReviewCount()
        );
    }

    @Test
    @DisplayName("Should override builder defaults")
    void shouldOverrideBuilderDefaults() {

        Product product = Product.builder()
                .sku("SKU-001")
                .name("Laptop")
                .category("Electronics")
                .price(BigDecimal.valueOf(1000))
                .currency(Currency.EUR)
                .quantityInStock(50)
                .reservedQuantity(5)
                .lowStockThreshold(3)
                .isActive(false)
                .isFeatured(true)
                .rating(BigDecimal.valueOf(4.5))
                .reviewCount(25)
                .build();

        assertEquals(Currency.EUR, product.getCurrency());

        assertEquals(
                Integer.valueOf(50),
                product.getQuantityInStock()
        );

        assertEquals(
                Integer.valueOf(5),
                product.getReservedQuantity()
        );

        assertEquals(
                Integer.valueOf(3),
                product.getLowStockThreshold()
        );

        assertFalse(product.getIsActive());

        assertTrue(product.getIsFeatured());

        assertEquals(
                BigDecimal.valueOf(4.5),
                product.getRating()
        );

        assertEquals(
                Integer.valueOf(25),
                product.getReviewCount()
        );
    }

    @Test
    @DisplayName("Should support full product name and SKU creation")
    void shouldCreateProductSuccessfully() {

        Product product = Product.builder()
                .sku("SKU-100")
                .name("MacBook Pro")
                .category("Electronics")
                .price(BigDecimal.valueOf(2500))
                .build();

        assertNotNull(product);

        assertEquals(
                "SKU-100",
                product.getSku()
        );

        assertEquals(
                "MacBook Pro",
                product.getName()
        );
    }
}