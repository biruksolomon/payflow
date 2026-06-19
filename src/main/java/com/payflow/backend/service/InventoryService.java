package com.payflow.backend.service;

import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * InventoryService — manages stock levels for products.
 *
 * Three distinct operations:
 *   reserveInventory       — called at order creation; increases reservedQuantity so the
 *                            stock cannot be sold to someone else while the order is pending.
 *   releaseReservedInventory — called on order cancellation; decreases reservedQuantity
 *                              without touching quantityInStock.
 *   deductInventory        — called after successful payment; decreases both
 *                            quantityInStock and reservedQuantity to permanently consume stock.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryService {

    private final ProductRepository productRepository;

    // ─────────────────────────────────────────────────────────────
    // RESERVE
    // ─────────────────────────────────────────────────────────────

    /**
     * Soft-lock {@code quantity} units for a pending order.
     * Throws {@link AuthException} with INSUFFICIENT_INVENTORY if available stock is too low.
     */
    @Transactional
    public void reserveInventory(Long productId, Integer quantity) {
        Product product = findProduct(productId);

        int available = product.getAvailableQuantity();
        if (available < quantity) {
            log.warn("Insufficient inventory for product={} requested={} available={}",
                    productId, quantity, available);
            throw new AuthException(
                    "Insufficient inventory for product: " + product.getName(),
                    "INSUFFICIENT_INVENTORY");
        }

        product.setReservedQuantity(
                (product.getReservedQuantity() != null ? product.getReservedQuantity() : 0) + quantity);

        productRepository.save(product);
        log.info("Inventory reserved — productId={} qty={} newReserved={}",
                productId, quantity, product.getReservedQuantity());
    }

    // ─────────────────────────────────────────────────────────────
    // RELEASE
    // ─────────────────────────────────────────────────────────────

    /**
     * Undo a prior reservation (order cancelled before payment).
     * Safe to call even when reservedQuantity is already 0.
     */
    @Transactional
    public void releaseReservedInventory(Long productId, Integer quantity) {
        Product product = findProduct(productId);

        int current = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
        product.setReservedQuantity(Math.max(0, current - quantity));

        productRepository.save(product);
        log.info("Inventory released — productId={} qty={} newReserved={}",
                productId, quantity, product.getReservedQuantity());
    }

    // ─────────────────────────────────────────────────────────────
    // DEDUCT
    // ─────────────────────────────────────────────────────────────

    /**
     * Permanently consume stock after successful payment.
     * Decrements both {@code quantityInStock} and {@code reservedQuantity}.
     */
    @Transactional
    public void deductInventory(Long productId, Integer quantity) {
        Product product = findProduct(productId);

        int newStock = product.getQuantityInStock() - quantity;
        if (newStock < 0) {
            log.error("Deduct would result in negative stock — productId={} qty={} current={}",
                    productId, quantity, product.getQuantityInStock());
            throw new AuthException(
                    "Cannot deduct inventory below zero for product: " + product.getName(),
                    "INVENTORY_ERROR");
        }
        product.setQuantityInStock(newStock);

        int reserved = product.getReservedQuantity() != null ? product.getReservedQuantity() : 0;
        product.setReservedQuantity(Math.max(0, reserved - quantity));

        productRepository.save(product);
        log.info("Inventory deducted — productId={} qty={} newStock={} newReserved={}",
                productId, quantity, product.getQuantityInStock(), product.getReservedQuantity());
    }

    // ─────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Product> getLowStockProducts() {
        return productRepository.findLowStockProducts();
    }

    @Transactional(readOnly = true)
    public int getAvailableQuantity(Long productId) {
        return findProduct(productId).getAvailableQuantity();
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE
    // ─────────────────────────────────────────────────────────────

    private Product findProduct(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new AuthException(
                        "Product not found: " + productId, "PRODUCT_NOT_FOUND"));
    }
}