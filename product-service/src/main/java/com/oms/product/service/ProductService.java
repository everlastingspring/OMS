package com.oms.product.service;

import com.oms.common.dto.PageResponse;
import com.oms.product.dto.ProductRequest;
import com.oms.product.dto.ProductResponse;
import com.oms.product.dto.ProductUpdateRequest;
import com.oms.product.dto.StockOperationRequest;
import com.oms.product.dto.StockOperationResponse;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;

public interface ProductService {

    ProductResponse create(ProductRequest request);

    ProductResponse update(Long id, ProductUpdateRequest request);

    void softDelete(Long id);

    ProductResponse getById(Long id);

    ProductResponse getBySku(String sku);

    PageResponse<ProductResponse> search(String keyword, Long categoryId, String categoryName,
                                         BigDecimal minPrice, BigDecimal maxPrice, Boolean inStockOnly,
                                         Pageable pageable);

    /** Called by order-service when an order is placed. Decrements under an optimistic lock. */
    StockOperationResponse reserveStock(StockOperationRequest request);

    /** Compensating action when an order is cancelled. */
    StockOperationResponse releaseStock(StockOperationRequest request);
}
