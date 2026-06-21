package com.payflow.backend.service;

import com.payflow.backend.domain.entity.Cart;
import com.payflow.backend.domain.entity.CartItem;
import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.exception.AuthException;
import com.payflow.backend.exception.UserNotFoundException;
import com.payflow.backend.repository.CartItemRepository;
import com.payflow.backend.repository.CartRepository;
import com.payflow.backend.repository.ProductRepository;
import com.payflow.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

/**
 * CartService — manages the per-user shopping cart.
 *
 * Each user has at most one Cart (OneToOne).  A Cart is created lazily on
 * the first addItem call and cleared (but not deleted) after a successful
 * order is placed.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class CartService {

    private final CartRepository cartRepository;
    private final CartItemRepository cartItemRepository;
    private final ProductRepository productRepository;
    private final UserRepository userRepository;

    // ─────────────────────────────────────────────────────────────
    // GET / CREATE CART
    // ─────────────────────────────────────────────────────────────

    /**
     * Returns the user's cart, creating an empty one if it does not exist yet.
     */
    @Transactional
    public Cart getOrCreateCart(Long userId) {
        return cartRepository.findByUserIdWithItems(userId)
                .orElseGet(() -> {
                    User user = userRepository.findActiveById(userId)
                            .orElseThrow(() -> new UserNotFoundException(userId));
                    Cart cart = Cart.builder().user(user).build();
                    Cart saved = cartRepository.save(cart);
                    log.info("Created new cart for userId={}", userId);
                    return saved;
                });
    }

    // ─────────────────────────────────────────────────────────────
    // ADD ITEM
    // ─────────────────────────────────────────────────────────────

    /**
     * Adds a product to the cart.  If the product is already present its
     * quantity is incremented; otherwise a new CartItem row is inserted.
     * Throws INSUFFICIENT_INVENTORY when requested qty exceeds available stock.
     */
    @Transactional
    public Cart addItem(Long userId, Long productId, int quantity) {
        if (quantity <= 0) {
            throw new AuthException("Quantity must be at least 1", "INVALID_QUANTITY");
        }

        Cart cart = getOrCreateCart(userId);
        Product product = findActiveProduct(productId);

        int available = product.getAvailableQuantity();
        if (available < quantity) {
            throw new AuthException(
                    "Insufficient stock for product: " + product.getName()
                            + " (available: " + available + ")",
                    "INSUFFICIENT_INVENTORY");
        }

        cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .ifPresentOrElse(
                        existing -> {
                            int newQty = existing.getQuantity() + quantity;
                            if (newQty > available) {
                                throw new AuthException(
                                        "Cannot add " + quantity + " more units — only "
                                                + available + " available.",
                                        "INSUFFICIENT_INVENTORY");
                            }
                            existing.setQuantity(newQty);
                            cartItemRepository.save(existing);
                            log.info("Updated cart item qty — cartId={} productId={} newQty={}",
                                    cart.getId(), productId, newQty);
                        },
                        () -> {
                            CartItem item = CartItem.builder()
                                    .cart(cart)
                                    .product(product)
                                    .quantity(quantity)
                                    .unitPrice(product.getEffectivePrice())
                                    .build();
                            cartItemRepository.save(item);
                            cart.addItem(item);
                            log.info("Added cart item — cartId={} productId={} qty={}",
                                    cart.getId(), productId, quantity);
                        });

        return recalculate(cart);
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE ITEM QUANTITY
    // ─────────────────────────────────────────────────────────────

    /**
     * Sets the quantity of an existing cart item.
     * Passing quantity = 0 removes the item entirely.
     */
    @Transactional
    public Cart updateItemQuantity(Long userId, Long productId, int quantity) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new AuthException(
                        "Product not in cart: " + productId, "ITEM_NOT_IN_CART"));

        if (quantity <= 0) {
            return removeItem(userId, productId);
        }

        Product product = item.getProduct();
        if (product.getAvailableQuantity() < quantity) {
            throw new AuthException(
                    "Insufficient stock for product: " + product.getName(),
                    "INSUFFICIENT_INVENTORY");
        }

        item.setQuantity(quantity);
        cartItemRepository.save(item);
        log.info("Cart item qty updated — cartId={} productId={} qty={}", cart.getId(), productId, quantity);
        return recalculate(cart);
    }

    // ─────────────────────────────────────────────────────────────
    // REMOVE ITEM
    // ─────────────────────────────────────────────────────────────

    @Transactional
    public Cart removeItem(Long userId, Long productId) {
        Cart cart = getOrCreateCart(userId);
        CartItem item = cartItemRepository.findByCartIdAndProductId(cart.getId(), productId)
                .orElseThrow(() -> new AuthException(
                        "Product not in cart: " + productId, "ITEM_NOT_IN_CART"));

        cart.removeItem(item);
        cartItemRepository.delete(item);
        log.info("Cart item removed — cartId={} productId={}", cart.getId(), productId);
        return recalculate(cart);
    }

    // ─────────────────────────────────────────────────────────────
    // CLEAR CART
    // ─────────────────────────────────────────────────────────────

    /**
     * Removes all items from the cart (called after order creation).
     */
    @Transactional
    public void clearCart(Long userId) {
        cartRepository.findByUserIdWithItems(userId).ifPresent(cart -> {
            cart.getItems().clear();
            recalculate(cart);
            log.info("Cart cleared for userId={}", userId);
        });
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    /**
     * Recomputes all monetary totals on the cart from its current items, then persists.
     */
    private Cart recalculate(Cart cart) {
        BigDecimal subtotal = cart.getItems().stream()
                .map(CartItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        cart.setTotalItems(cart.getItems().size());
        cart.setSubtotal(subtotal);
        cart.setTaxAmount(subtotal.multiply(BigDecimal.valueOf(0.10)));
        cart.setTotalPrice(cart.getSubtotal()
                .add(cart.getTaxAmount())
                .subtract(cart.getDiscountAmount() != null ? cart.getDiscountAmount() : BigDecimal.ZERO));

        return cartRepository.save(cart);
    }

    private Product findActiveProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new AuthException(
                        "Product not found: " + productId, "PRODUCT_NOT_FOUND"));
        if (!product.getIsActive()) {
            throw new AuthException("Product is not available: " + product.getName(), "PRODUCT_UNAVAILABLE");
        }
        return product;
    }
}