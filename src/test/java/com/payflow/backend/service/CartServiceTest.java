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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceTest {

    @Mock
    private CartRepository cartRepository;

    @Mock
    private CartItemRepository cartItemRepository;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartService cartService;

    private User user;
    private Cart cart;
    private Product product;
    private CartItem cartItem;

    @BeforeEach
    void setUp() {

        user = User.builder()
                .id(1L)
                .email("user@test.com")
                .build();

        cart = Cart.builder()
                .id(1L)
                .user(user)
                .items(new ArrayList<>())
                .subtotal(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .totalPrice(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .build();

        product = Product.builder()
                .id(100L)
                .sku("SKU-001")
                .name("Laptop")
                .category("Electronics")
                .price(BigDecimal.valueOf(1000))
                .quantityInStock(20)
                .reservedQuantity(0)
                .isActive(true)
                .build();

        cartItem = CartItem.builder()
                .id(1L)
                .cart(cart)
                .product(product)
                .quantity(2)
                .unitPrice(BigDecimal.valueOf(1000))
                .build();
    }

    // ============================================================
    // GET OR CREATE CART
    // ============================================================

    @Test
    void shouldReturnExistingCart() {

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        Cart result = cartService.getOrCreateCart(1L);

        assertNotNull(result);
        assertEquals(1L, result.getId());

        verify(cartRepository).findByUserIdWithItems(1L);
        verify(cartRepository, never()).save(any());
    }

    @Test
    void shouldCreateCartWhenMissing() {

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.empty());

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.of(user));

        when(cartRepository.save(any(Cart.class)))
                .thenReturn(cart);

        Cart result = cartService.getOrCreateCart(1L);

        assertNotNull(result);

        verify(userRepository).findActiveById(1L);
        verify(cartRepository).save(any(Cart.class));
    }

    @Test
    void shouldThrowWhenUserNotFoundCreatingCart() {

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.empty());

        when(userRepository.findActiveById(1L))
                .thenReturn(Optional.empty());

        assertThrows(
                UserNotFoundException.class,
                () -> cartService.getOrCreateCart(1L)
        );
    }

    // ============================================================
    // ADD ITEM
    // ============================================================

    @Test
    void shouldAddNewItemToCart() {

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.findByCartIdAndProductId(1L, 100L))
                .thenReturn(Optional.empty());

        when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Cart result = cartService.addItem(1L, 100L, 3);

        assertNotNull(result);
        assertEquals(1, result.getItems().size());

        verify(cartItemRepository).save(any(CartItem.class));
    }

    @Test
    void shouldIncreaseQuantityWhenItemAlreadyExists() {

        cart.getItems().add(cartItem);

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        when(cartItemRepository.findByCartIdAndProductId(1L, 100L))
                .thenReturn(Optional.of(cartItem));

        when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        cartService.addItem(1L, 100L, 3);

        assertEquals(5, cartItem.getQuantity());

        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void shouldThrowWhenQuantityInvalid() {

        AuthException ex = assertThrows(
                AuthException.class,
                () -> cartService.addItem(1L, 100L, 0)
        );

        assertEquals("INVALID_QUANTITY", ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenProductNotFound() {

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(100L))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(
                AuthException.class,
                () -> cartService.addItem(1L, 100L, 1)
        );

        assertEquals("PRODUCT_NOT_FOUND", ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenProductInactive() {

        product.setIsActive(false);

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        AuthException ex = assertThrows(
                AuthException.class,
                () -> cartService.addItem(1L, 100L, 1)
        );

        assertEquals("PRODUCT_UNAVAILABLE", ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenStockInsufficient() {

        product.setQuantityInStock(2);
        product.setReservedQuantity(0);

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(productRepository.findById(100L))
                .thenReturn(Optional.of(product));

        AuthException ex = assertThrows(
                AuthException.class,
                () -> cartService.addItem(1L, 100L, 5)
        );

        assertEquals("INSUFFICIENT_INVENTORY", ex.getErrorCode());
    }

    // ============================================================
    // UPDATE ITEM QUANTITY
    // ============================================================

    @Test
    void shouldUpdateItemQuantity() {

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByCartIdAndProductId(1L, 100L))
                .thenReturn(Optional.of(cartItem));

        when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Cart result = cartService.updateItemQuantity(1L, 100L, 7);

        assertNotNull(result);
        assertEquals(7, cartItem.getQuantity());

        verify(cartItemRepository).save(cartItem);
    }

    @Test
    void shouldRemoveItemWhenQuantityZero() {

        cart.getItems().add(cartItem);

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByCartIdAndProductId(1L, 100L))
                .thenReturn(Optional.of(cartItem));

        when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        cartService.updateItemQuantity(1L, 100L, 0);

        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void shouldThrowWhenUpdatingMissingItem() {

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByCartIdAndProductId(1L, 100L))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(
                AuthException.class,
                () -> cartService.updateItemQuantity(1L, 100L, 5)
        );

        assertEquals("ITEM_NOT_IN_CART", ex.getErrorCode());
    }

    @Test
    void shouldThrowWhenUpdateQuantityExceedsStock() {

        product.setQuantityInStock(3);
        product.setReservedQuantity(0);

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByCartIdAndProductId(1L, 100L))
                .thenReturn(Optional.of(cartItem));

        AuthException ex = assertThrows(
                AuthException.class,
                () -> cartService.updateItemQuantity(1L, 100L, 10)
        );

        assertEquals("INSUFFICIENT_INVENTORY", ex.getErrorCode());
    }

    // ============================================================
    // REMOVE ITEM
    // ============================================================

    @Test
    void shouldRemoveItemSuccessfully() {

        cart.getItems().add(cartItem);

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByCartIdAndProductId(1L, 100L))
                .thenReturn(Optional.of(cartItem));

        when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        Cart result = cartService.removeItem(1L, 100L);

        assertNotNull(result);

        verify(cartItemRepository).delete(cartItem);
    }

    @Test
    void shouldThrowWhenRemovingMissingItem() {

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(cartItemRepository.findByCartIdAndProductId(1L, 100L))
                .thenReturn(Optional.empty());

        AuthException ex = assertThrows(
                AuthException.class,
                () -> cartService.removeItem(1L, 100L)
        );

        assertEquals("ITEM_NOT_IN_CART", ex.getErrorCode());
    }

    // ============================================================
    // CLEAR CART
    // ============================================================

    @Test
    void shouldClearCart() {

        cart.getItems().add(cartItem);

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.of(cart));

        when(cartRepository.save(any(Cart.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        cartService.clearCart(1L);

        assertTrue(cart.getItems().isEmpty());

        verify(cartRepository).save(cart);
    }

    @Test
    void shouldDoNothingWhenCartNotFound() {

        when(cartRepository.findByUserIdWithItems(1L))
                .thenReturn(Optional.empty());

        cartService.clearCart(1L);

        verify(cartRepository, never()).save(any());
    }
}