package com.oms.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.oms.common.dto.PageResponse;
import com.oms.common.exception.GlobalExceptionHandler;
import com.oms.common.exception.ResourceNotFoundException;
import com.oms.product.dto.ProductRequest;
import com.oms.product.dto.ProductResponse;
import com.oms.product.service.ProductService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.Collections;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Standalone MockMvc: mapping, query-parameter binding, validation and error
 * translation. Role checks live in SecurityConfig and @PreAuthorize and are not
 * exercised by this slice - that is deliberate, not an omission.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProductController")
class ProductControllerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Mock
    private ProductService productService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ProductController(productService))
                .setCustomArgumentResolvers(new PageableHandlerMethodArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private ProductResponse productResponse() {
        ProductResponse response = new ProductResponse();
        response.setId(3L);
        response.setSku("ELEC-AUD-003");
        response.setName("Pulse ANC Wireless Headphones");
        response.setPrice(new BigDecimal("7499.00"));
        response.setStockQuantity(120);
        response.setInStock(true);
        response.setCategoryId(1L);
        response.setCategoryName("Electronics");
        response.setActive(true);
        return response;
    }

    private ProductRequest validRequest() {
        ProductRequest request = new ProductRequest();
        request.setSku("ELEC-AUD-003");
        request.setName("Pulse ANC Wireless Headphones");
        request.setDescription("Active noise cancelling");
        request.setPrice(new BigDecimal("7499.00"));
        request.setStockQuantity(120);
        request.setCategoryId(1L);
        return request;
    }

    @Test
    @DisplayName("POST /products returns 201 with the created product")
    void create_valid_returns201() throws Exception {
        when(productService.create(any(ProductRequest.class))).thenReturn(productResponse());

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.sku").value("ELEC-AUD-003"))
                .andExpect(jsonPath("$.data.inStock").value(true));
    }

    @Test
    @DisplayName("POST /products returns 400 for a lower-case SKU")
    void create_lowerCaseSku_returns400() throws Exception {
        ProductRequest request = validRequest();
        request.setSku("elec-aud-003");

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors[0].field").value("sku"));
    }

    @Test
    @DisplayName("POST /products returns 400 for a zero price")
    void create_zeroPrice_returns400() throws Exception {
        ProductRequest request = validRequest();
        request.setPrice(BigDecimal.ZERO);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("price"));
    }

    @Test
    @DisplayName("POST /products returns 400 for negative stock")
    void create_negativeStock_returns400() throws Exception {
        ProductRequest request = validRequest();
        request.setStockQuantity(-1);

        mockMvc.perform(post("/api/v1/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(MAPPER.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("stockQuantity"));
    }

    @Test
    @DisplayName("GET /products/{id} returns 404 for an unknown id")
    void getById_unknown_returns404() throws Exception {
        when(productService.getById(404L))
                .thenThrow(new ResourceNotFoundException("Product", "id", 404L));

        mockMvc.perform(get("/api/v1/products/404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("GET /products/{id} returns 400 when the id is not a number")
    void getById_nonNumericId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/products/not-a-number"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("TYPE_MISMATCH"));
    }

    @Test
    @DisplayName("GET /products/search binds every filter and returns a paged envelope")
    void search_bindsFiltersAndPaging() throws Exception {
        PageResponse<ProductResponse> page = PageResponse.of(
                new PageImpl<>(Collections.singletonList(productResponse()), PageRequest.of(0, 5), 1),
                p -> p);

        when(productService.search(eq("pulse"), eq(1L), ArgumentMatchers.isNull(),
                eq(new BigDecimal("1000")), eq(new BigDecimal("9000")), eq(Boolean.TRUE),
                any(Pageable.class)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/products/search")
                        .param("keyword", "pulse")
                        .param("categoryId", "1")
                        .param("minPrice", "1000")
                        .param("maxPrice", "9000")
                        .param("inStock", "true")
                        .param("page", "0")
                        .param("size", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.content[0].sku").value("ELEC-AUD-003"))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.first").value(true));
    }

    @Test
    @DisplayName("DELETE /products/{id} returns 204")
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/v1/products/3"))
                .andExpect(status().isNoContent());
    }
}
