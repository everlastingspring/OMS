package com.oms.product.controller;

import com.oms.common.dto.ApiResponse;
import com.oms.common.dto.PageResponse;
import com.oms.product.dto.ProductRequest;
import com.oms.product.dto.ProductResponse;
import com.oms.product.dto.ProductUpdateRequest;
import com.oms.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.math.BigDecimal;

@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Tag(name = "Products", description = "Catalogue management and search")
public class ProductController {

    private final ProductService productService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Add a product (ADMIN only)",
            description = "SKU must be unique. Returns 409 if it already exists.")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(created, "Product created"));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Update a product (ADMIN only)",
            description = "SKU is immutable and is not accepted in the body.")
    public ResponseEntity<ApiResponse<ProductResponse>> update(@PathVariable Long id,
                                                               @Valid @RequestBody ProductUpdateRequest request) {
        return ResponseEntity.ok(ApiResponse.success(productService.update(id, request), "Product updated"));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @SecurityRequirement(name = "bearerAuth")
    @Operation(summary = "Delete a product (ADMIN only)",
            description = "Soft delete. The row survives so historic order lines still resolve.")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.softDelete(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a product by id")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success(productService.getById(id)));
    }

    @GetMapping("/sku/{sku}")
    @Operation(summary = "Get a product by SKU")
    public ResponseEntity<ApiResponse<ProductResponse>> getBySku(@PathVariable String sku) {
        return ResponseEntity.ok(ApiResponse.success(productService.getBySku(sku)));
    }

    @GetMapping("/search")
    @Operation(summary = "Search the catalogue",
            description = "All filters are optional and combine with AND. Soft-deleted products are "
                    + "never returned. Sort with ?sort=price,asc or ?sort=name,desc.")
    public ResponseEntity<ApiResponse<PageResponse<ProductResponse>>> search(
            @Parameter(description = "Matches name, SKU or description, case-insensitively")
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @Parameter(description = "Category name, as an alternative to categoryId")
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @Parameter(description = "true hides out-of-stock products")
            @RequestParam(required = false) Boolean inStock,
            @PageableDefault(size = 20, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {

        PageResponse<ProductResponse> results = productService.search(
                keyword, categoryId, category, minPrice, maxPrice, inStock, pageable);
        return ResponseEntity.ok(ApiResponse.success(results));
    }
}
