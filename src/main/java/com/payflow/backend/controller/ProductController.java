package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
@Tag(name = "Products", description = "Product catalogue management")
public class ProductController {

    private final ProductService productService;

    // ─────────────────────────────────────────────────────────────
    // PUBLIC — catalogue reads
    // ─────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "List all active products")
    public ResponseEntity<List<Product>> getAllProducts() {
        return ResponseEntity.ok(productService.getAllActiveProducts());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(productService.getById(id));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU")
    public ResponseEntity<Product> getProductBySku(@PathVariable String sku) {
        return ResponseEntity.ok(productService.getBySku(sku));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "List products by category")
    public ResponseEntity<List<Product>> getByCategory(@PathVariable String category) {
        return ResponseEntity.ok(productService.getProductsByCategory(category));
    }

    @GetMapping("/search")
    @Operation(summary = "Search products by name or description")
    public ResponseEntity<List<Product>> search(@RequestParam String q) {
        return ResponseEntity.ok(productService.searchProducts(q));
    }

    @GetMapping("/featured")
    @Operation(summary = "List featured products")
    public ResponseEntity<List<Product>> getFeatured() {
        return ResponseEntity.ok(productService.getFeaturedProducts());
    }

    // ─────────────────────────────────────────────────────────────
    // ADMIN — mutations
    // ─────────────────────────────────────────────────────────────

    @PostMapping
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Create a new product (admin only)")
    public ResponseEntity<Product> createProduct(
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        PayFlowUserDetails admin = resolveUser(authentication);

        Product product = productService.createProduct(
                (String) body.get("sku"),
                (String) body.get("name"),
                (String) body.get("description"),
                (String) body.get("category"),
                parseBigDecimal(body.get("price")),
                parseBigDecimal(body.get("discountPrice")),
                parseInteger(body.get("quantityInStock")),
                parseInteger(body.get("lowStockThreshold")),
                (String) body.get("imageUrl"),
                (String) body.get("thumbnailUrl"),
                Boolean.TRUE.equals(body.get("isFeatured")),
                admin.getId());

        log.info("Product created by adminId={}", admin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update product (admin only)")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {

        Product updated = productService.updateProduct(
                id,
                (String) body.get("name"),
                (String) body.get("description"),
                (String) body.get("category"),
                parseBigDecimal(body.get("price")),
                parseBigDecimal(body.get("discountPrice")),
                parseInteger(body.get("quantityInStock")),
                parseInteger(body.get("lowStockThreshold")),
                (String) body.get("imageUrl"),
                (String) body.get("thumbnailUrl"),
                (Boolean) body.get("isFeatured"),
                (Boolean) body.get("isActive"));

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Deactivate product (admin only, soft-delete)")
    public ResponseEntity<Map<String, String>> deactivateProduct(@PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.ok(Map.of("message", "Product deactivated successfully"));
    }

    @GetMapping("/admin/low-stock")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "List low-stock products (admin only)")
    public ResponseEntity<List<Product>> getLowStock() {
        return ResponseEntity.ok(productService.getLowStockProducts());
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private PayFlowUserDetails resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new com.payflow.backend.exception.AuthException("Authentication required", "UNAUTHORIZED");
        }
        return (PayFlowUserDetails) authentication.getPrincipal();
    }

    private BigDecimal parseBigDecimal(Object value) {
        if (value == null) return null;
        if (value instanceof BigDecimal bd) return bd;
        if (value instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(value.toString()); } catch (Exception e) { return null; }
    }

    private Integer parseInteger(Object value) {
        if (value == null) return null;
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); } catch (Exception e) { return null; }
    }
}
