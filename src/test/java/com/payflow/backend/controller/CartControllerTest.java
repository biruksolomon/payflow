package com.payflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.config.SecurityConfig;
import com.payflow.backend.config.TestWebMvcSecurityConfig;
import com.payflow.backend.domain.entity.Cart;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.exception.AuthException;
import org.springframework.http.HttpStatus;
import com.payflow.backend.security.JwtAuthenticationFilter;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.CartService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = CartController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestWebMvcSecurityConfig.class)
class CartControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private CartService cartService;

    private Cart emptyCart;
    private UsernamePasswordAuthenticationToken userToken;

    @BeforeEach
    void setUp() {
        User user = User.builder()
                .id(1L)
                .email("user@test.com")
                .passwordHash("hashed")
                .firstName("John")
                .lastName("Doe")
                .userRole(UserRole.CUSTOMER)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
        PayFlowUserDetails userDetails = new PayFlowUserDetails(user);
        userToken = new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());

        emptyCart = Cart.builder()
                .id(1L)
                .user(user)
                .totalItems(0)
                .subtotal(BigDecimal.ZERO)
                .taxAmount(BigDecimal.ZERO)
                .discountAmount(BigDecimal.ZERO)
                .totalPrice(BigDecimal.ZERO)
                .items(new ArrayList<>())
                .build();
    }

    // ─────────────────────────────────────────────────────────────
    // GET CART
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnCartForAuthenticatedUser() throws Exception {
        when(cartService.getOrCreateCart(1L)).thenReturn(emptyCart);

        mockMvc.perform(get("/api/cart")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.totalItems").value(0));

        verify(cartService).getOrCreateCart(1L);
    }

    // ─────────────────────────────────────────────────────────────
    // ADD ITEM
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldAddItemToCart() throws Exception {
        Cart updatedCart = Cart.builder().id(1L).totalItems(1)
                .subtotal(new BigDecimal("99.99")).taxAmount(new BigDecimal("10.00"))
                .totalPrice(new BigDecimal("109.99")).items(new ArrayList<>()).build();

        when(cartService.addItem(1L, 10L, 2)).thenReturn(updatedCart);

        mockMvc.perform(post("/api/cart/items")
                        .with(authentication(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", 10, "quantity", 2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1));

        verify(cartService).addItem(1L, 10L, 2);
    }

    @Test
    void shouldReturn400WhenProductIdMissing() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(authentication(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 2))))
                .andExpect(status().isBadRequest());

        verify(cartService, never()).addItem(any(), any(), anyInt());
    }

    @Test
    void shouldReturn400WhenQuantityMissing() throws Exception {
        mockMvc.perform(post("/api/cart/items")
                        .with(authentication(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("productId", 10))))
                .andExpect(status().isBadRequest());

        verify(cartService, never()).addItem(any(), any(), anyInt());
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE ITEM
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldUpdateCartItemQuantity() throws Exception {
        when(cartService.updateItemQuantity(1L, 10L, 3)).thenReturn(emptyCart);

        mockMvc.perform(put("/api/cart/items/10")
                        .with(authentication(userToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("quantity", 3))))
                .andExpect(status().isOk());

        verify(cartService).updateItemQuantity(1L, 10L, 3);
    }

    // ─────────────────────────────────────────────────────────────
    // REMOVE ITEM
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldRemoveItemFromCart() throws Exception {
        when(cartService.removeItem(1L, 10L)).thenReturn(emptyCart);

        mockMvc.perform(delete("/api/cart/items/10")
                        .with(authentication(userToken)))
                .andExpect(status().isOk());

        verify(cartService).removeItem(1L, 10L);
    }

    @Test
    void shouldReturn404WhenItemNotInCart() throws Exception {
        when(cartService.removeItem(1L, 99L))
                .thenThrow(new AuthException("Product not in cart: 99", "ITEM_NOT_IN_CART", HttpStatus.NOT_FOUND));

        mockMvc.perform(delete("/api/cart/items/99")
                        .with(authentication(userToken)))
                .andExpect(status().isNotFound());

        verify(cartService).removeItem(1L, 99L);
    }

    // ─────────────────────────────────────────────────────────────
    // CLEAR CART
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldClearCartSuccessfully() throws Exception {
        doNothing().when(cartService).clearCart(1L);

        mockMvc.perform(delete("/api/cart")
                        .with(authentication(userToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Cart cleared successfully"));

        verify(cartService).clearCart(1L);
    }
}