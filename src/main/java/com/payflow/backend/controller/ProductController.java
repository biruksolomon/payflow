package com.payflow.backend.controller;

import com.payflow.backend.dto.request.CreateProductRequest;
import com.payflow.backend.dto.request.UpdateProductRequest;
import com.payflow.backend.dto.response.MessageResponse;
import com.payflow.backend.dto.response.ProductResponse;
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
import java.util.stream.Collectors;

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
    public ResponseEntity<List<ProductResponse>> getAllProducts() {
        List<ProductResponse> products = productService.getAllActiveProducts()
                .stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(products);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get product by ID")
    public ResponseEntity<ProductResponse> getProductById(@PathVariable Long id) {
        return ResponseEntity.ok(ProductResponse.from(productService.getById(id)));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get product by SKU")
    public ResponseEntity<ProductResponse> getProductBySku(@PathVariable String sku) {
        return ResponseEntity.ok(ProductResponse.from(productService.getBySku(sku)));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "List products by category")
    public ResponseEntity<List<ProductResponse>> getByCategory(@PathVariable String category) {
        List<ProductResponse> products = productService.getProductsByCategory(category)
                .stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(products);
    }

    @GetMapping("/search")
    @Operation(summary = "Search products by name or description")
    public ResponseEntity<List<ProductResponse>> search(@RequestParam String q) {
        List<ProductResponse> products = productService.searchProducts(q)
                .stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(products);
    }

    @GetMapping("/featured")
    @Operation(summary = "List featured products")
    public ResponseEntity<List<ProductResponse>> getFeatured() {
        List<ProductResponse> products = productService.getFeaturedProducts()
                .stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(products);
    }

    // ─────────────────────────────────────────────────────────────
    // ADMIN — mutations
    // ─────────────────────────────────────────────────────────────

    @PostMapping
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Create a new product (admin only)")
    public ResponseEntity<ProductResponse> createProduct(
            @Valid @RequestBody CreateProductRequest request,
            Authentication authentication) {

        PayFlowUserDetails admin = resolveUser(authentication);

        ProductResponse response = ProductResponse.from(
                productService.createProduct(
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
                        admin.getId()));

        log.info("Product created by adminId={}", admin.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PutMapping("/{id}")
    @SecurityRequirement(name = "Bearer Authentication")
    @Operation(summary = "Update product (admin only)")
    public ResponseEntity<ProductResponse> updateProduct(
            @PathVariable Long id,
            @Valid @RequestBody UpdateProductRequest request) {

        ProductResponse response = ProductResponse.from(
                productService.updateProduct(
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
                        request.getIsActive()));

        return ResponseEntity.ok(response);
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
    public ResponseEntity<List<ProductResponse>> getLowStock() {
        List<ProductResponse> products = productService.getLowStockProducts()
                .stream()
                .map(ProductResponse::from)
                .collect(Collectors.toList());
        return ResponseEntity.ok(products);
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
