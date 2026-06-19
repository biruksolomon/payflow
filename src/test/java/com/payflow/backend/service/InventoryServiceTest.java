package com.payflow.backend.service;

import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private InventoryService inventoryService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .name("iPhone 15")
                .quantityInStock(100)
                .reservedQuantity(10)
                .build();
    }

    // ==========================================================
    // RESERVE INVENTORY
    // ==========================================================

    @Test
    void shouldReserveInventorySuccessfully() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        inventoryService.reserveInventory(1L, 20);

        ArgumentCaptor<Product> captor =
                ArgumentCaptor.forClass(Product.class);

        verify(productRepository).save(captor.capture());

        Product saved = captor.getValue();

        assertEquals(30, saved.getReservedQuantity());
    }

    @Test
    void shouldReserveInventoryWhenReservedQuantityNull() {

        product.setReservedQuantity(null);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        inventoryService.reserveInventory(1L, 5);

        verify(productRepository).save(product);

        assertEquals(5, product.getReservedQuantity());
    }

    @Test
    void shouldThrowWhenInventoryInsufficient() {

        product.setQuantityInStock(10);
        product.setReservedQuantity(8);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        AuthException ex = assertThrows(
                AuthException.class,
                () -> inventoryService.reserveInventory(1L, 5)
        );

        assertEquals("INSUFFICIENT_INVENTORY", ex.getErrorCode());

        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenReserveProductNotFound() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(
                AuthException.class,
                () -> inventoryService.reserveInventory(1L, 5)
        );

        assertEquals("PRODUCT_NOT_FOUND", ex.getErrorCode());

        verify(productRepository, never()).save(any());
    }

    // ==========================================================
    // RELEASE INVENTORY
    // ==========================================================

    @Test
    void shouldReleaseReservedInventorySuccessfully() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        inventoryService.releaseReservedInventory(1L, 5);

        verify(productRepository).save(product);

        assertEquals(5, product.getReservedQuantity());
    }

    @Test
    void shouldNotReleaseBelowZero() {

        product.setReservedQuantity(3);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        inventoryService.releaseReservedInventory(1L, 10);

        verify(productRepository).save(product);

        assertEquals(0, product.getReservedQuantity());
    }

    @Test
    void shouldReleaseWhenReservedQuantityNull() {

        product.setReservedQuantity(null);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        inventoryService.releaseReservedInventory(1L, 10);

        verify(productRepository).save(product);

        assertEquals(0, product.getReservedQuantity());
    }

    // ==========================================================
    // DEDUCT INVENTORY
    // ==========================================================

    @Test
    void shouldDeductInventorySuccessfully() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        inventoryService.deductInventory(1L, 5);

        verify(productRepository).save(product);

        assertEquals(95, product.getQuantityInStock());
        assertEquals(5, product.getReservedQuantity());
    }

    @Test
    void shouldDeductInventoryWhenReservedQuantityNull() {

        product.setReservedQuantity(null);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        inventoryService.deductInventory(1L, 5);

        verify(productRepository).save(product);

        assertEquals(95, product.getQuantityInStock());
        assertEquals(0, product.getReservedQuantity());
    }

    @Test
    void shouldNotAllowNegativeStock() {

        product.setQuantityInStock(3);

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        AuthException ex = assertThrows(
                AuthException.class,
                () -> inventoryService.deductInventory(1L, 5)
        );

        assertEquals("INVENTORY_ERROR", ex.getErrorCode());

        verify(productRepository, never()).save(any());
    }

    @Test
    void shouldThrowWhenDeductProductNotFound() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(
                AuthException.class,
                () -> inventoryService.deductInventory(1L, 1)
        );

        assertEquals("PRODUCT_NOT_FOUND", ex.getErrorCode());

        verify(productRepository, never()).save(any());
    }

    // ==========================================================
    // GET LOW STOCK PRODUCTS
    // ==========================================================

    @Test
    void shouldReturnLowStockProducts() {

        Product p1 = Product.builder()
                .id(2L)
                .name("MacBook")
                .build();

        List<Product> expected = List.of(p1);

        when(productRepository.findLowStockProducts())
                .thenReturn(expected);

        List<Product> result =
                inventoryService.getLowStockProducts();

        assertEquals(1, result.size());
        assertEquals(expected, result);

        verify(productRepository).findLowStockProducts();
    }

    // ==========================================================
    // GET AVAILABLE QUANTITY
    // ==========================================================

    @Test
    void shouldReturnAvailableQuantity() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.of(product));

        int available =
                inventoryService.getAvailableQuantity(1L);

        assertEquals(90, available);
    }

    @Test
    void shouldThrowWhenGettingAvailableQuantityForMissingProduct() {

        when(productRepository.findById(1L))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(
                AuthException.class,
                () -> inventoryService.getAvailableQuantity(1L)
        );

        assertEquals("PRODUCT_NOT_FOUND", ex.getErrorCode());
    }
}