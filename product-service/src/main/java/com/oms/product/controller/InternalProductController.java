package com.oms.product.controller;

import com.oms.common.dto.ApiResponse;
import com.oms.product.dto.StockOperationRequest;
import com.oms.product.dto.StockOperationResponse;
import com.oms.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;

/**
 * Called by order-service only. Guarded by InternalApiKeyInterceptor.
 * Returns 409 for insufficient stock and 409 for a lost optimistic-lock race
 * after three retries, so the caller can distinguish "no stock" from "try again".
 */
@RestController
@RequestMapping("/api/v1/internal/products")
@RequiredArgsConstructor
@Tag(name = "Internal", description = "Service-to-service endpoints, not for public clients")
public class InternalProductController {

    private final ProductService productService;

    @PostMapping("/reserve-stock")
    @Operation(summary = "Reserve stock for an order",
            description = "Decrements stock for every line under an optimistic lock. "
                    + "All-or-nothing: one insufficient line rolls the whole request back.")
    public ResponseEntity<ApiResponse<StockOperationResponse>> reserve(
            @Valid @RequestBody StockOperationRequest request) {
        StockOperationResponse response = productService.reserveStock(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Stock reserved"));
    }

    @PostMapping("/release-stock")
    @Operation(summary = "Release previously reserved stock",
            description = "Compensating action used when an order is cancelled.")
    public ResponseEntity<ApiResponse<StockOperationResponse>> release(
            @Valid @RequestBody StockOperationRequest request) {
        StockOperationResponse response = productService.releaseStock(request);
        return ResponseEntity.ok(ApiResponse.success(response, "Stock released"));
    }
}
