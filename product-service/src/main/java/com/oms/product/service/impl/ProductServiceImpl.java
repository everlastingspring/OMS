package com.oms.product.service.impl;

import com.oms.common.dto.PageResponse;
import com.oms.common.exception.DuplicateResourceException;
import com.oms.common.exception.InvalidOperationException;
import com.oms.common.exception.ResourceNotFoundException;
import com.oms.product.dto.ProductRequest;
import com.oms.product.dto.ProductResponse;
import com.oms.product.dto.ProductUpdateRequest;
import com.oms.product.dto.StockItemRequest;
import com.oms.product.dto.StockLineResponse;
import com.oms.product.dto.StockOperationRequest;
import com.oms.product.dto.StockOperationResponse;
import com.oms.product.entity.Category;
import com.oms.product.entity.Product;
import com.oms.product.mapper.ProductMapper;
import com.oms.product.repository.CategoryRepository;
import com.oms.product.repository.ProductRepository;
import com.oms.product.service.ProductService;
import com.oms.product.specification.ProductSpecifications;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductServiceImpl implements ProductService {

    /**
     * Two orders racing for the same product will collide on @Version. Retrying
     * re-reads the row and usually succeeds; three attempts is enough for genuine
     * contention and short enough not to hide a real problem.
     */
    private static final int MAX_STOCK_ATTEMPTS = 3;

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;
    private final TransactionTemplate transactionTemplate;

    // ---------------------------------------------------------------- CRUD

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        String sku = request.getSku().trim().toUpperCase();
        if (productRepository.existsBySku(sku)) {
            throw new DuplicateResourceException("Product", "sku", sku);
        }

        Product product = new Product();
        product.setSku(sku);
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(requireCategory(request.getCategoryId()));
        product.setActive(true);

        Product saved = productRepository.save(product);
        log.info("Created product id={} sku={} price={} stock={}",
                saved.getId(), saved.getSku(), saved.getPrice(), saved.getStockQuantity());
        return ProductMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest request) {
        Product product = requireProduct(id);
        if (!product.isActive()) {
            throw new InvalidOperationException("Product " + product.getSku()
                    + " is deleted and cannot be updated");
        }
        product.setName(request.getName().trim());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setStockQuantity(request.getStockQuantity());
        product.setCategory(requireCategory(request.getCategoryId()));
        log.info("Updated product id={} sku={}", id, product.getSku());
        return ProductMapper.toResponse(product);
    }

    @Override
    @Transactional
    public void softDelete(Long id) {
        Product product = requireProduct(id);
        if (!product.isActive()) {
            throw new InvalidOperationException("Product " + product.getSku() + " is already deleted");
        }
        // Soft delete: order_items reference product_id, and an order placed last
        // month must still render even after the product leaves the catalogue.
        product.setActive(false);
        log.info("Soft deleted product id={} sku={}", id, product.getSku());
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(Long id) {
        return ProductMapper.toResponse(requireProduct(id));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getBySku(String sku) {
        Product product = productRepository.findBySku(sku.trim().toUpperCase())
                .orElseThrow(() -> new ResourceNotFoundException("Product", "sku", sku));
        return ProductMapper.toResponse(product);
    }

    // -------------------------------------------------------------- Search

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> search(String keyword, Long categoryId, String categoryName,
                                                BigDecimal minPrice, BigDecimal maxPrice,
                                                Boolean inStockOnly, Pageable pageable) {
        if (minPrice != null && maxPrice != null && minPrice.compareTo(maxPrice) > 0) {
            throw new InvalidOperationException("minPrice cannot be greater than maxPrice");
        }

        // Specification.and(null) is a no-op in Spring Data, so every filter is
        // chained unconditionally and each one decides for itself whether to apply.
        Specification<Product> spec = Specification.where(ProductSpecifications.activeOnly())
                .and(ProductSpecifications.keywordMatches(keyword))
                .and(ProductSpecifications.inCategory(categoryId))
                .and(ProductSpecifications.categoryNamed(categoryName))
                .and(ProductSpecifications.priceAtLeast(minPrice))
                .and(ProductSpecifications.priceAtMost(maxPrice))
                .and(ProductSpecifications.inStockOnly(inStockOnly));

        Page<Product> page = productRepository.findAll(spec, pageable);
        log.debug("Product search keyword='{}' category={} returned {} of {} results",
                keyword, categoryId, page.getNumberOfElements(), page.getTotalElements());
        return PageResponse.of(page, ProductMapper::toResponse);
    }

    // --------------------------------------------------------------- Stock

    @Override
    public StockOperationResponse reserveStock(StockOperationRequest request) {
        return applyWithRetry(request, true);
    }

    @Override
    public StockOperationResponse releaseStock(StockOperationRequest request) {
        return applyWithRetry(request, false);
    }

    /**
     * The retry has to sit outside the transaction: a failed optimistic lock
     * marks the transaction rollback-only, so the row must be re-read in a fresh
     * one. TransactionTemplate is used instead of @Transactional because a
     * self-invocation would bypass the proxy and silently run without a retry.
     */
    private StockOperationResponse applyWithRetry(StockOperationRequest request, boolean reserve) {
        String operation = reserve ? "reserve" : "release";
        OptimisticLockingFailureException lastFailure = null;

        for (int attempt = 1; attempt <= MAX_STOCK_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> applyStockChange(request, reserve));
            } catch (OptimisticLockingFailureException ex) {
                lastFailure = ex;
                log.warn("Stock {} for order {} hit a concurrent update (attempt {}/{})",
                        operation, request.getOrderReference(), attempt, MAX_STOCK_ATTEMPTS);
                backOff(attempt);
            }
        }

        log.error("Stock {} for order {} failed after {} attempts",
                operation, request.getOrderReference(), MAX_STOCK_ATTEMPTS);
        throw lastFailure;
    }

    private StockOperationResponse applyStockChange(StockOperationRequest request, boolean reserve) {
        Map<Long, Integer> quantityByProduct = mergeDuplicateLines(request.getItems());

        List<Long> ids = new ArrayList<>(quantityByProduct.keySet());
        Map<Long, Product> productsById = productRepository.findAllByIdIn(ids).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));

        List<StockLineResponse> lines = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Map.Entry<Long, Integer> entry : quantityByProduct.entrySet()) {
            Long productId = entry.getKey();
            int quantity = entry.getValue();

            Product product = productsById.get(productId);
            if (product == null) {
                throw new ResourceNotFoundException("Product", "id", productId);
            }
            if (reserve && !product.isActive()) {
                throw new InvalidOperationException(
                        "Product " + product.getSku() + " is no longer available");
            }

            if (reserve) {
                product.reserve(quantity);
            } else {
                product.release(quantity);
            }

            BigDecimal lineTotal = product.getPrice().multiply(BigDecimal.valueOf(quantity));
            total = total.add(lineTotal);
            lines.add(new StockLineResponse(product.getId(), product.getSku(), product.getName(),
                    quantity, product.getPrice(), product.getStockQuantity()));
        }

        log.info("Stock {} applied for order {}: {} line(s), total {}",
                reserve ? "reserved" : "released", request.getOrderReference(), lines.size(), total);
        return new StockOperationResponse(request.getOrderReference(), total, lines);
    }

    /** Two lines for the same product are one decrement, not two racing ones. */
    private Map<Long, Integer> mergeDuplicateLines(List<StockItemRequest> items) {
        Map<Long, Integer> merged = new LinkedHashMap<>();
        for (StockItemRequest item : items) {
            merged.merge(item.getProductId(), item.getQuantity(), Integer::sum);
        }
        return merged;
    }

    private void backOff(int attempt) {
        try {
            Thread.sleep(50L * attempt);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    // ------------------------------------------------------------- Helpers

    private Product requireProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Product", "id", id));
    }

    private Category requireCategory(Long categoryId) {
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", categoryId));
        if (!category.isActive()) {
            throw new InvalidOperationException("Category '" + category.getName() + "' is not active");
        }
        return category;
    }
}
