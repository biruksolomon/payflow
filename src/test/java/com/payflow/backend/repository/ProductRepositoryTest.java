package com.payflow.backend.repository;


import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.domain.enums.Currency;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    private Product createProduct(
            String sku,
            String name,
            String category,
            boolean active,
            boolean featured
    ) {
        return Product.builder()
                .sku(sku)
                .name(name)
                .description(name + " description")
                .category(category)
                .price(BigDecimal.valueOf(100))
                .currency(Currency.USD)
                .quantityInStock(50)
                .reservedQuantity(0)
                .lowStockThreshold(10)
                .isActive(active)
                .isFeatured(featured)
                .build();
    }

    @Test
    @DisplayName("Should find product by SKU")
    void shouldFindBySku() {

        Product product =
                createProduct(
                        "SKU-001",
                        "iPhone 15",
                        "Electronics",
                        true,
                        false
                );

        productRepository.save(product);

        Optional<Product> result =
                productRepository.findBySku("SKU-001");

        assertTrue(result.isPresent());

        assertEquals(
                "iPhone 15",
                result.get().getName()
        );
    }

    @Test
    @DisplayName("Should return empty when SKU not found")
    void shouldReturnEmptyWhenSkuNotFound() {

        Optional<Product> result =
                productRepository.findBySku("UNKNOWN");

        assertTrue(result.isEmpty());
    }

    @Test
    @DisplayName("Should find active products")
    void shouldFindActiveProducts() {

        productRepository.save(
                createProduct(
                        "SKU-1",
                        "Product 1",
                        "Electronics",
                        true,
                        false
                )
        );

        productRepository.save(
                createProduct(
                        "SKU-2",
                        "Product 2",
                        "Electronics",
                        false,
                        false
                )
        );

        List<Product> products =
                productRepository.findByIsActiveTrue();

        assertEquals(1, products.size());

        assertTrue(products.get(0).getIsActive());
    }

    @Test
    @DisplayName("Should find products by category")
    void shouldFindProductsByCategory() {

        productRepository.save(
                createProduct(
                        "SKU-1",
                        "Laptop",
                        "Electronics",
                        true,
                        false
                )
        );

        productRepository.save(
                createProduct(
                        "SKU-2",
                        "T-Shirt",
                        "Clothing",
                        true,
                        false
                )
        );

        List<Product> products =
                productRepository.findByCategoryAndIsActiveTrue("Electronics");

        assertEquals(1, products.size());

        assertEquals(
                "Electronics",
                products.get(0).getCategory()
        );
    }

    @Test
    @DisplayName("Should find active products by category")
    void shouldFindActiveProductsByCategory() {

        productRepository.save(
                createProduct(
                        "SKU-1",
                        "Laptop",
                        "Electronics",
                        true,
                        false
                )
        );

        productRepository.save(
                createProduct(
                        "SKU-2",
                        "Tablet",
                        "Electronics",
                        false,
                        false
                )
        );

        List<Product> products =
                productRepository
                        .findByIsActiveTrueAndCategoryOrderByCreatedAtDesc(
                                "Electronics"
                        );

        assertEquals(1, products.size());

        assertEquals(
                "Laptop",
                products.get(0).getName()
        );
    }

    @Test
    @DisplayName("Should search products by name")
    void shouldSearchByName() {

        productRepository.save(
                createProduct(
                        "SKU-1",
                        "Gaming Laptop",
                        "Electronics",
                        true,
                        false
                )
        );

        productRepository.save(
                createProduct(
                        "SKU-2",
                        "Office Chair",
                        "Furniture",
                        true,
                        false
                )
        );

        List<Product> products =
                productRepository.searchProducts("Laptop");

        assertEquals(1, products.size());

        assertEquals(
                "Gaming Laptop",
                products.get(0).getName()
        );
    }

    @Test
    @DisplayName("Should search products by description")
    void shouldSearchByDescription() {

        Product product =
                createProduct(
                        "SKU-1",
                        "Keyboard",
                        "Electronics",
                        true,
                        false
                );

        product.setDescription(
                "Mechanical gaming keyboard"
        );

        productRepository.save(product);

        List<Product> products =
                productRepository.searchProducts("gaming");

        assertEquals(1, products.size());
    }

    @Test
    @DisplayName("Should ignore inactive products during search")
    void shouldIgnoreInactiveProducts() {

        productRepository.save(
                createProduct(
                        "SKU-1",
                        "Gaming Laptop",
                        "Electronics",
                        false,
                        false
                )
        );

        List<Product> products =
                productRepository.searchProducts("Laptop");

        assertTrue(products.isEmpty());
    }

    @Test
    @DisplayName("Should find featured products")
    void shouldFindFeaturedProducts() {

        productRepository.save(
                createProduct(
                        "SKU-1",
                        "Featured Product",
                        "Electronics",
                        true,
                        true
                )
        );

        productRepository.save(
                createProduct(
                        "SKU-2",
                        "Normal Product",
                        "Electronics",
                        true,
                        false
                )
        );

        List<Product> products =
                productRepository.findFeaturedProducts();

        assertEquals(1, products.size());

        assertTrue(
                products.get(0).getIsFeatured()
        );
    }

    @Test
    @DisplayName("Should find low stock products")
    void shouldFindLowStockProducts() {

        Product lowStock =
                createProduct(
                        "SKU-1",
                        "Low Stock Product",
                        "Electronics",
                        true,
                        false
                );

        lowStock.setQuantityInStock(5);
        lowStock.setReservedQuantity(0);
        lowStock.setLowStockThreshold(10);

        Product healthyStock =
                createProduct(
                        "SKU-2",
                        "Healthy Product",
                        "Electronics",
                        true,
                        false
                );

        healthyStock.setQuantityInStock(50);
        healthyStock.setReservedQuantity(0);
        healthyStock.setLowStockThreshold(10);

        productRepository.save(lowStock);
        productRepository.save(healthyStock);

        List<Product> products =
                productRepository.findLowStockProducts();

        assertEquals(1, products.size());

        assertEquals(
                "Low Stock Product",
                products.get(0).getName()
        );
    }

    @Test
    @DisplayName("Should consider reserved quantity when checking stock")
    void shouldConsiderReservedQuantity() {

        Product product =
                createProduct(
                        "SKU-1",
                        "Reserved Product",
                        "Electronics",
                        true,
                        false
                );

        product.setQuantityInStock(20);
        product.setReservedQuantity(15);
        product.setLowStockThreshold(10);

        productRepository.save(product);

        List<Product> products =
                productRepository.findLowStockProducts();

        assertEquals(1, products.size());
    }

    @Test
    @DisplayName("Should save and retrieve product")
    void shouldSaveAndRetrieveProduct() {

        Product product =
                createProduct(
                        "SKU-100",
                        "MacBook Pro",
                        "Electronics",
                        true,
                        true
                );

        Product saved =
                productRepository.save(product);

        Product found =
                productRepository.findById(saved.getId())
                        .orElseThrow();

        assertEquals(
                saved.getId(),
                found.getId()
        );

        assertEquals(
                "MacBook Pro",
                found.getName()
        );
    }
}