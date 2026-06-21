package com.payflow.backend.service;

import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.ProductRepository;
import com.payflow.backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductService productService;

    private Product product;
    private User admin;

    @BeforeEach
    void setUp() {

        admin = User.builder()
                .id(1L)
                .email("admin@test.com")
                .build();

        product = Product.builder()
                .id(100L)
                .sku("SKU-001")
                .name("Laptop")
                .description("Gaming Laptop")
                .category("Electronics")
                .price(BigDecimal.valueOf(1500))
                .discountPrice(BigDecimal.valueOf(1200))
                .quantityInStock(50)
                .lowStockThreshold(10)
                .isActive(true)
                .isFeatured(false)
                .createdBy(admin)
                .build();
    }

    // ============================================================
    // CREATE PRODUCT
    // ============================================================

    @Test
    void shouldCreateProductSuccessfully() {

        when(productRepository.findBySku("SKU-001"))
                .thenReturn(Optional.empty());

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(admin));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.createProduct(
                "SKU-001",
                "Laptop",
                "Gaming Laptop",
                "Electronics",
                BigDecimal.valueOf(1500),
                BigDecimal.valueOf(1200),
                50,
                10,
                "image.jpg",
                "thumb.jpg",
                true,
                1L
        );

        assertNotNull(result);
        assertEquals("SKU-001", result.getSku());
        assertEquals("Laptop", result.getName());
        assertEquals(admin, result.getCreatedBy());

        verify(productRepository).save(any(Product.class));
    }

    @Test
    void shouldThrowWhenSkuAlreadyExists() {

        when(productRepository.findBySku("SKU-001"))
                .thenReturn(Optional.of(product));

        AuthException ex = assertThrows(
                AuthException.class,
                () -> productService.createProduct(
                        "SKU-001",
                        "Laptop",
                        "Desc",
                        "Electronics",
                        BigDecimal.TEN,
                        null,
                        5,
                        10,
                        null,
                        null,
                        false,
                        1L
                )
        );

        assertEquals("DUPLICATE_SKU", ex.getErrorCode());

        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenAdminNotFound() {

        when(productRepository.findBySku("SKU-001"))
                .thenReturn(Optional.empty());

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> productService.createProduct(
                        "SKU-001",
                        "Laptop",
                        "Desc",
                        "Electronics",
                        BigDecimal.TEN,
                        null,
                        5,
                        10,
                        null,
                        null,
                        false,
                        1L
                )
        );
    }

    @Test
    void shouldUseDefaultValuesWhenNullProvided() {

        when(productRepository.findBySku(anyString()))
                .thenReturn(Optional.empty());

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(admin));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        productService.createProduct(
                "SKU-001",
                "Laptop",
                "Desc",
                "Electronics",
                BigDecimal.TEN,
                null,
                null,
                null,
                null,
                null,
                false,
                1L
        );

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);

        verify(productRepository).save(captor.capture());

        Product saved = captor.getValue();

        assertEquals(0, saved.getQuantityInStock());
        assertEquals(10, saved.getLowStockThreshold());
    }

    // ============================================================
    // UPDATE PRODUCT
    // ============================================================

    @Test
    void shouldUpdateProductSuccessfully() {

        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        when(productRepository.save(any(Product.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Product updated = productService.updateProduct(
                100L,
                "MacBook",
                "Updated Description",
                "Computers",
                BigDecimal.valueOf(2000),
                BigDecimal.valueOf(1800),
                100,
                5,
                "new-image.jpg",
                "new-thumb.jpg",
                true,
                false
        );

        assertEquals("MacBook", updated.getName());
        assertEquals("Updated Description", updated.getDescription());
        assertEquals("Computers", updated.getCategory());
        assertEquals(100, updated.getQuantityInStock());
        assertFalse(updated.getIsActive());
        assertTrue(updated.getIsFeatured());
    }

    @Test
    void shouldThrowWhenUpdatingMissingProduct() {

        when(productRepository.findById(999L))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(
                AuthException.class,
                () -> productService.updateProduct(
                        999L,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null
                )
        );

        assertEquals("PRODUCT_NOT_FOUND", ex.getErrorCode());
    }

    // ============================================================
    // DEACTIVATE PRODUCT
    // ============================================================

    @Test
    void shouldDeactivateProductSuccessfully() {

        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        productService.deactivateProduct(100L);

        assertFalse(product.getIsActive());

        verify(productRepository).save(product);
    }

    @Test
    void shouldThrowWhenDeactivatingMissingProduct() {

        when(productRepository.findById(100L))
                .thenReturn(Optional.empty());

        assertThrows(
                AuthException.class,
                () -> productService.deactivateProduct(100L)
        );
    }

    // ============================================================
    // GET BY ID
    // ============================================================

    @Test
    void shouldGetByIdSuccessfully() {

        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        Product result = productService.getById(100L);

        assertEquals(100L, result.getId());
    }

    @Test
    void shouldThrowWhenGetByIdNotFound() {

        when(productRepository.findById(100L))
                .thenReturn(Optional.empty());

        assertThrows(
                AuthException.class,
                () -> productService.getById(100L)
        );
    }

    // ============================================================
    // GET BY SKU
    // ============================================================

    @Test
    void shouldGetBySkuSuccessfully() {

        when(productRepository.findBySku("SKU-001"))
                .thenReturn(Optional.of(product));

        Product result = productService.getBySku("SKU-001");

        assertEquals("SKU-001", result.getSku());
    }

    @Test
    void shouldThrowWhenSkuNotFound() {

        when(productRepository.findBySku("SKU-001"))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(
                AuthException.class,
                () -> productService.getBySku("SKU-001")
        );

        assertEquals("PRODUCT_NOT_FOUND", ex.getErrorCode());
    }

    // ============================================================
    // LIST QUERIES
    // ============================================================

    @Test
    void shouldGetAllActiveProducts() {

        when(productRepository.findByIsActiveTrue())
                .thenReturn(List.of(product));

        List<Product> result = productService.getAllActiveProducts();

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetProductsByCategory() {

        when(productRepository.findByCategoryAndIsActiveTrue("Electronics"))
                .thenReturn(List.of(product));

        List<Product> result =
                productService.getProductsByCategory("Electronics");

        assertEquals(1, result.size());
    }

    @Test
    void shouldSearchProducts() {

        when(productRepository.searchProducts("Laptop"))
                .thenReturn(List.of(product));

        List<Product> result =
                productService.searchProducts("Laptop");

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetFeaturedProducts() {

        when(productRepository.findFeaturedProducts())
                .thenReturn(List.of(product));

        List<Product> result =
                productService.getFeaturedProducts();

        assertEquals(1, result.size());
    }

    @Test
    void shouldGetLowStockProducts() {

        when(productRepository.findLowStockProducts())
                .thenReturn(List.of(product));

        List<Product> result =
                productService.getLowStockProducts();

        assertEquals(1, result.size());
    }
}