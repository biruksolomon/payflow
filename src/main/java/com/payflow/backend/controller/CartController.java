package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.Cart;
import com.payflow.backend.dto.request.AddCartItemRequest;
import com.payflow.backend.dto.request.UpdateCartItemRequest;
import com.payflow.backend.dto.response.MessageResponse;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/cart")
@RequiredArgsConstructor
@Tag(name = "Cart", description = "Shopping cart management")
@SecurityRequirement(name = "Bearer Authentication")
public class CartController {

    private final CartService cartService;

    // ─────────────────────────────────────────────────────────────
    // GET CART
    // ─────────────────────────────────────────────────────────────

    @GetMapping
    @Operation(summary = "Get current user's cart")
    public ResponseEntity<Cart> getCart(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        return ResponseEntity.ok(cartService.getOrCreateCart(userId));
    }

    // ─────────────────────────────────────────────────────────────
    // ADD ITEM
    // ─────────────────────────────────────────────────────────────

    @PostMapping("/items")
    @Operation(summary = "Add a product to the cart")
    public ResponseEntity<Cart> addItem(
            @Valid @RequestBody AddCartItemRequest request,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        Cart cart = cartService.addItem(userId, request.getProductId(), request.getQuantity());
        log.info("Item added to cart — userId={} productId={} qty={}", userId, request.getProductId(), request.getQuantity());
        return ResponseEntity.ok(cart);
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE ITEM QUANTITY
    // ─────────────────────────────────────────────────────────────

    @PutMapping("/items/{productId}")
    @Operation(summary = "Update quantity of a cart item (0 removes it)")
    public ResponseEntity<Cart> updateItem(
            @PathVariable Long productId,
            @Valid @RequestBody UpdateCartItemRequest request,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        Cart cart = cartService.updateItemQuantity(userId, productId, request.getQuantity());
        log.info("Cart item updated — userId={} productId={} qty={}", userId, productId, request.getQuantity());
        return ResponseEntity.ok(cart);
    }

    // ─────────────────────────────────────────────────────────────
    // REMOVE ITEM
    // ─────────────────────────────────────────────────────────────

    @DeleteMapping("/items/{productId}")
    @Operation(summary = "Remove a product from the cart")
    public ResponseEntity<Cart> removeItem(
            @PathVariable Long productId,
            Authentication authentication) {

        Long userId = resolveUserId(authentication);
        Cart cart   = cartService.removeItem(userId, productId);
        log.info("Cart item removed — userId={} productId={}", userId, productId);
        return ResponseEntity.ok(cart);
    }

    // ─────────────────────────────────────────────────────────────
    // CLEAR CART
    // ─────────────────────────────────────────────────────────────

    @DeleteMapping
    @Operation(summary = "Clear all items from the cart")
    public ResponseEntity<MessageResponse> clearCart(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        cartService.clearCart(userId);
        log.info("Cart cleared — userId={}", userId);
        return ResponseEntity.ok(MessageResponse.of("Cart cleared successfully"));
    }

    // ─────────────────────────────────────────────────────────────
    // HELPER
    // ─────────────────────────────────────────────────────────────

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required", "UNAUTHORIZED");
        }
        return ((PayFlowUserDetails) authentication.getPrincipal()).getId();
    }
}
