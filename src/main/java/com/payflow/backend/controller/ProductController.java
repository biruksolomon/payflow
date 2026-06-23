package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.dto.request.CreateProductRequest;
import com.payflow.backend.dto.request.UpdateProductRequest;
import com.payflow.backend.dto.response.MessageResponse;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
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
            @Valid @RequestBody CreateProductRequest request,
            Authentication authentication) {

        PayFlowUserDetails admin = resolveUser(authentication);

        Product product = productService.createProduct(
                request.getSku(),
                request.getName(),
                request.getDescription(),
                request.getCategory(),
                request.getPrice(),
                request.getDiscountPrice(),
                request.getQuantityInStock(),
                request.getLowStockThreshold(),
                request.getImageUrl(),
                request.getThumbnailUrl(),
                Boolean.TRUE.equals(request.getIsFeatured()),
                admin.getId());

        log.info("Product created by adminId={}", admin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(product);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update product (admin only)")
    public ResponseEntity<Product> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        Product updated = productService.updateProduct(
                id,
                request.getName(),
                request.getDescription(),
                request.getCategory(),
                request.getPrice(),
                request.getDiscountPrice(),
                request.getQuantityInStock(),
                request.getLowStockThreshold(),
                request.getImageUrl(),
                request.getThumbnailUrl(),
                request.getIsFeatured(),
                request.getIsActive());

        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Deactivate product (admin only, soft-delete)")
    public ResponseEntity<MessageResponse> deactivateProduct(@PathVariable Long id) {
        productService.deactivateProduct(id);
        return ResponseEntity.ok(MessageResponse.of("Product deactivated successfully"));
    }

    @GetMapping("/admin/low-stock")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "List low-stock products (admin only)")
    public ResponseEntity<List<Product>> getLowStock() {
        return ResponseEntity.ok(productService.getLowStockProducts());
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────

    private PayFlowUserDetails resolveUser(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required", "UNAUTHORIZED");
        }
        return (PayFlowUserDetails) authentication.getPrincipal();
    }
}
