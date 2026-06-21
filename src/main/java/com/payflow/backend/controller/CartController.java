package com.payflow.backend.controller;

import com.payflow.backend.domain.entity.Cart;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.security.PayFlowUserDetails;
import org.springframework.http.HttpStatus;
import com.payflow.backend.service.CartService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        Long userId    = resolveUserId(authentication);
        Long productId = parseLong(body.get("productId"));
        int  quantity  = parseIntRequired(body.get("quantity"));

        if (productId == null) {
            throw new AuthException("productId is required", "INVALID_REQUEST", HttpStatus.BAD_REQUEST);
        }

        Cart cart = cartService.addItem(userId, productId, quantity);
        log.info("Item added to cart — userId={} productId={} qty={}", userId, productId, quantity);
        return ResponseEntity.ok(cart);
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE ITEM QUANTITY
    // ─────────────────────────────────────────────────────────────

    @PutMapping("/items/{productId}")
    @Operation(summary = "Update quantity of a cart item (0 removes it)")
    public ResponseEntity<Cart> updateItem(
            @PathVariable Long productId,
            @RequestBody Map<String, Object> body,
            Authentication authentication) {

        Long userId   = resolveUserId(authentication);
        int  quantity = parseIntRequired(body.get("quantity"));

        Cart cart = cartService.updateItemQuantity(userId, productId, quantity);
        log.info("Cart item updated — userId={} productId={} qty={}", userId, productId, quantity);
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
    public ResponseEntity<Map<String, String>> clearCart(Authentication authentication) {
        Long userId = resolveUserId(authentication);
        cartService.clearCart(userId);
        log.info("Cart cleared — userId={}", userId);
        return ResponseEntity.ok(Map.of("message", "Cart cleared successfully"));
    }

    // ─────────────────────────────────────────────────────────────
    // HELPERS
    // ─────────────────────────────────────────────────────────────

    private Long resolveUserId(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) {
            throw new AuthException("Authentication required", "UNAUTHORIZED");
        }
        return ((PayFlowUserDetails) authentication.getPrincipal()).getId();
    }

    private Long parseLong(Object value) {
        if (value == null) return null;
        if (value instanceof Long l) return l;
        if (value instanceof Number n) return n.longValue();
        try { return Long.parseLong(value.toString()); } catch (Exception e) { return null; }
    }

    private int parseIntRequired(Object value) {
        if (value == null) throw new AuthException("quantity is required", "INVALID_REQUEST", HttpStatus.BAD_REQUEST);
        if (value instanceof Integer i) return i;
        if (value instanceof Number n) return n.intValue();
        try { return Integer.parseInt(value.toString()); }
        catch (Exception e) { throw new AuthException("quantity must be a number", "INVALID_REQUEST", HttpStatus.BAD_REQUEST); }
    }
}
