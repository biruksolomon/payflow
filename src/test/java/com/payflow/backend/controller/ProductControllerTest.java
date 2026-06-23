package com.payflow.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payflow.backend.config.SecurityConfig;
import com.payflow.backend.config.TestWebMvcSecurityConfig;
import com.payflow.backend.domain.entity.Product;
import com.payflow.backend.domain.entity.User;
import com.payflow.backend.domain.enums.AccountStatus;
import com.payflow.backend.domain.enums.UserRole;
import com.payflow.backend.exception.AuthException;
import org.springframework.http.HttpStatus;
import com.payflow.backend.security.JwtAuthenticationFilter;
import com.payflow.backend.security.PayFlowUserDetails;
import com.payflow.backend.service.ProductService;
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

import com.payflow.backend.dto.request.CreateProductRequest;
import com.payflow.backend.dto.request.UpdateProductRequest;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ProductController.class,
        excludeFilters = {
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = SecurityConfig.class),
                @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class)
        }
)
@Import(TestWebMvcSecurityConfig.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    private Product product;

    private PayFlowUserDetails adminDetails;
    private UsernamePasswordAuthenticationToken adminToken;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .sku("SKU-001")
                .name("Test Product")
                .description("A test product")
                .category("Electronics")
                .price(new BigDecimal("99.99"))
                .quantityInStock(50)
                .isActive(true)
                .isFeatured(false)
                .build();

        User admin = User.builder()
                .id(10L)
                .email("admin@test.com")
                .passwordHash("hashed")
                .firstName("Admin")
                .lastName("User")
                .userRole(UserRole.ADMIN)
                .accountStatus(AccountStatus.ACTIVE)
                .emailVerified(true)
                .isDeleted(false)
                .build();
        adminDetails = new PayFlowUserDetails(admin);
        adminToken = new UsernamePasswordAuthenticationToken(adminDetails, null, adminDetails.getAuthorities());
    }

    // ─────────────────────────────────────────────────────────────
    // GET ALL
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnAllActiveProducts() throws Exception {
        when(productService.getAllActiveProducts()).thenReturn(List.of(product));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-001"))
                .andExpect(jsonPath("$[0].name").value("Test Product"));

        verify(productService).getAllActiveProducts();
    }

    // ─────────────────────────────────────────────────────────────
    // GET BY ID
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnProductById() throws Exception {
        when(productService.getById(1L)).thenReturn(product);

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.name").value("Test Product"));

        verify(productService).getById(1L);
    }

    @Test
    void shouldReturn404WhenProductNotFound() throws Exception {
        when(productService.getById(99L))
                .thenThrow(new AuthException("Product not found: 99", "PRODUCT_NOT_FOUND", HttpStatus.NOT_FOUND));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound());

        verify(productService).getById(99L);
    }

    // ─────────────────────────────────────────────────────────────
    // GET BY SKU
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnProductBySku() throws Exception {
        when(productService.getBySku("SKU-001")).thenReturn(product);

        mockMvc.perform(get("/api/products/sku/SKU-001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sku").value("SKU-001"));

        verify(productService).getBySku("SKU-001");
    }

    // ─────────────────────────────────────────────────────────────
    // GET BY CATEGORY
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnProductsByCategory() throws Exception {
        when(productService.getProductsByCategory("Electronics")).thenReturn(List.of(product));

        mockMvc.perform(get("/api/products/category/Electronics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].category").value("Electronics"));

        verify(productService).getProductsByCategory("Electronics");
    }

    // ─────────────────────────────────────────────────────────────
    // SEARCH
    // ───────────────────────��─────────────────────────────────────

    @Test
    void shouldSearchProducts() throws Exception {
        when(productService.searchProducts("test")).thenReturn(List.of(product));

        mockMvc.perform(get("/api/products/search").param("q", "test"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Test Product"));

        verify(productService).searchProducts("test");
    }

    // ─────────────────────────────────────────────────────────────
    // FEATURED
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnFeaturedProducts() throws Exception {
        Product featured = Product.builder().id(2L).sku("FEAT-001").name("Featured").isFeatured(true).isActive(true).build();
        when(productService.getFeaturedProducts()).thenReturn(List.of(featured));

        mockMvc.perform(get("/api/products/featured"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("FEAT-001"));

        verify(productService).getFeaturedProducts();
    }

    // ─────────────────────────────────────────────────────────────
    // CREATE (admin)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldCreateProductSuccessfully() throws Exception {
        when(productService.createProduct(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong()))
                .thenReturn(product);

        CreateProductRequest request = CreateProductRequest.builder()
                .sku("SKU-001")
                .name("Test Product")
                .description("A test product")
                .category("Electronics")
                .price(new BigDecimal("99.99"))
                .quantityInStock(50)
                .isFeatured(false)
                .build();

        mockMvc.perform(post("/api/products")
                        .with(authentication(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sku").value("SKU-001"));

        verify(productService).createProduct(any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), anyBoolean(), anyLong());
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE (admin)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldUpdateProductSuccessfully() throws Exception {
        Product updated = Product.builder().id(1L).sku("SKU-001").name("Updated Name").isActive(true).isFeatured(false).build();
        when(productService.updateProduct(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(updated);

        UpdateProductRequest request = UpdateProductRequest.builder()
                .name("Updated Name")
                .build();

        mockMvc.perform(put("/api/products/1")
                        .with(authentication(adminToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Updated Name"));

        verify(productService).updateProduct(eq(1L), any(), any(), any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    // ─────────────────────────────────────────────────────────────
    // DEACTIVATE (admin)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldDeactivateProduct() throws Exception {
        doNothing().when(productService).deactivateProduct(1L);

        mockMvc.perform(delete("/api/products/1")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Product deactivated successfully"));

        verify(productService).deactivateProduct(1L);
    }

    // ─────────────────────────────────────────────────────────────
    // LOW STOCK (admin)
    // ─────────────────────────────────────────────────────────────

    @Test
    void shouldReturnLowStockProducts() throws Exception {
        when(productService.getLowStockProducts()).thenReturn(List.of(product));

        mockMvc.perform(get("/api/products/admin/low-stock")
                        .with(authentication(adminToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sku").value("SKU-001"));

        verify(productService).getLowStockProducts();
    }
}
