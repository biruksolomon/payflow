package com.payflow.backend.service;

import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.ProductRepository;
import com.payflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * ProductService — CRUD + catalogue queries for the Product domain.
 *
 * All mutating operations require the caller to supply the adminUserId so that
 * the audit field {@code createdBy} is populated and ownership can be logged.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class ProductService {

    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────
    // CREATE
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Product createProduct(
            String sku,
            String name,
            String description,
            String category,
            BigDecimal price,
            BigDecimal discountPrice,
            Integer quantityInStock,
            Integer lowStockThreshold,
            String imageUrl,
            String thumbnailUrl,
            boolean isFeatured,
            Long adminUserId) {

        if (productRepository.findBySku(sku).isPresent()) {
            throw new AuthException("Product with SKU already exists: " + sku, "DUPLICATE_SKU");
        }

        User admin = userRepository.findActiveById(adminUserId)
                .orElseThrow(() -> new UserNotFoundException(adminUserId));

        Product product = Product.builder()
                .sku(sku)
                .name(name)
                .description(description)
                .category(category)
                .price(price)
                .discountPrice(discountPrice)
                .quantityInStock(quantityInStock != null ? quantityInStock : 0)
                .lowStockThreshold(lowStockThreshold != null ? lowStockThreshold : 10)
                .imageUrl(imageUrl)
                .thumbnailUrl(thumbnailUrl)
                .isActive(true)
                .isFeatured(isFeatured)
                .createdBy(admin)
                .build();

        Product saved = productRepository.save(product);
        log.info("Product created — productId={} sku={} by adminId={}", saved.getId(), sku, adminUserId);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE
    // ────────────────────────────────────────────────────��────────

    @Transactional
    public Product updateProduct(
            Long productId,
            String name,
            String description,
            String category,
            BigDecimal price,
            BigDecimal discountPrice,
            Integer quantityInStock,
            Integer lowStockThreshold,
            String imageUrl,
            String thumbnailUrl,
            Boolean isFeatured,
            Boolean isActive) {

        Product product = findById(productId);

        if (name != null)             product.setName(name);
        if (description != null)      product.setDescription(description);
        if (category != null)         product.setCategory(category);
        if (price != null)            product.setPrice(price);
        if (discountPrice != null)    product.setDiscountPrice(discountPrice);
        if (quantityInStock != null)  product.setQuantityInStock(quantityInStock);
        if (lowStockThreshold != null) product.setLowStockThreshold(lowStockThreshold);
        if (imageUrl != null)         product.setImageUrl(imageUrl);
        if (thumbnailUrl != null)     product.setThumbnailUrl(thumbnailUrl);
        if (isFeatured != null)       product.setIsFeatured(isFeatured);
        if (isActive != null)         product.setIsActive(isActive);

        Product saved = productRepository.save(product);
        log.info("Product updated — productId={}", productId);
        return saved;
    }

    // ─────────────────────────────────────────────────────────────
    // DELETE (soft)
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public void deactivateProduct(Long productId) {
        Product product = findById(productId);
        product.setIsActive(false);
        productRepository.save(product);
        log.info("Product deactivated — productId={}", productId);
    }

    // ─────────────────────────────────────────────────────────────
    // QUERIES
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Product getById(Long productId) {
        return findById(productId);
    }

    @Transactional(readOnly = true)
    public Product getBySku(String sku) {
        return productRepository.findBySku(sku)
                .orElseThrow(() -> new AuthException("Product not found: " + sku, "PRODUCT_NOT_FOUND"));
    }

    @Transactional(readOnly = true)
    public List<Product> getAllActiveProducts() {
        return productRepository.findByIsActiveTrue();
    }

    @Transactional(readOnly = true)
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategoryAndIsActiveTrue(category);
    }

    @Transactional(readOnly = true)
    public List<Product> searchProducts(String searchTerm) {
        return productRepository.searchProducts(searchTerm);
    }

    @Transactional(readOnly = true)
    public List<Product> getFeaturedProducts() {
        return productRepository.findFeaturedProducts();
    }

    @Transactional(readOnly = true)
    public List<Product> getLowStockProducts() {
        return productRepository.findLowStockProducts();
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE
    // ─────────────────────────────────────────────────────────────

    private Product findById(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new AuthException(
                        "Product not found: " + productId, "PRODUCT_NOT_FOUND"));
    }
}