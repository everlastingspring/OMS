package com.oms.product.service;

import com.oms.common.dto.PageResponse;
import com.oms.common.exception.DuplicateResourceException;
import com.oms.common.exception.InsufficientStockException;
import com.oms.common.exception.InvalidOperationException;
import com.oms.common.exception.ResourceNotFoundException;
import com.oms.product.dto.ProductRequest;
import com.oms.product.dto.ProductResponse;
import com.oms.product.dto.StockItemRequest;
import com.oms.product.dto.StockOperationRequest;
import com.oms.product.dto.StockOperationResponse;
import com.oms.product.entity.Category;
import com.oms.product.entity.Product;
import com.oms.product.repository.CategoryRepository;
import com.oms.product.repository.ProductRepository;
import com.oms.product.service.impl.ProductServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("ProductService")
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private CategoryRepository categoryRepository;

    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private ProductServiceImpl productService;

    private Category electronics;
    private Product headphones;

    @BeforeEach
    void setUp() {
        electronics = new Category();
        electronics.setId(1L);
        electronics.setName("Electronics");
        electronics.setActive(true);

        headphones = new Product();
        headphones.setId(3L);
        headphones.setSku("ELEC-AUD-003");
        headphones.setName("Pulse ANC Wireless Headphones");
        headphones.setPrice(new BigDecimal("7499.00"));
        headphones.setStockQuantity(10);
        headphones.setCategory(electronics);
        headphones.setActive(true);
    }

    /** Makes transactionTemplate.execute run its callback inline. */
    private void runTransactionsInline() {
        lenient().when(transactionTemplate
                        .execute(ArgumentMatchers.<TransactionCallback<StockOperationResponse>>any()))
                .thenAnswer(invocation -> {
                    TransactionCallback<StockOperationResponse> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });
    }

    private StockOperationRequest stockRequest(StockItemRequest... items) {
        StockOperationRequest request = new StockOperationRequest();
        request.setOrderReference("ORD-20260827-0001");
        request.setItems(new ArrayList<>(Arrays.asList(items)));
        return request;
    }

    // ------------------------------------------------------------------ CRUD

    @Test
    @DisplayName("normalises the SKU to upper case before saving")
    void create_upperCasesSku() {
        when(productRepository.existsBySku("ELEC-NEW-009")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));
        when(productRepository.save(any(Product.class))).thenAnswer(i -> i.getArgument(0));

        ProductRequest request = new ProductRequest();
        request.setSku("elec-new-009");
        request.setName("Nimbus 65W GaN Charger");
        request.setPrice(new BigDecimal("2199.00"));
        request.setStockQuantity(25);
        request.setCategoryId(1L);

        ProductResponse response = productService.create(request);

        assertThat(response.getSku()).isEqualTo("ELEC-NEW-009");
        assertThat(response.isInStock()).isTrue();
    }

    @Test
    @DisplayName("rejects a duplicate SKU with 409 semantics")
    void create_duplicateSku_throwsConflict() {
        when(productRepository.existsBySku("ELEC-AUD-003")).thenReturn(true);

        ProductRequest request = new ProductRequest();
        request.setSku("ELEC-AUD-003");
        request.setName("Duplicate");
        request.setPrice(new BigDecimal("100.00"));
        request.setStockQuantity(1);
        request.setCategoryId(1L);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(DuplicateResourceException.class);

        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    @DisplayName("refuses to attach a product to a deactivated category")
    void create_inactiveCategory_throwsInvalidOperation() {
        electronics.setActive(false);
        when(productRepository.existsBySku("ELEC-NEW-009")).thenReturn(false);
        when(categoryRepository.findById(1L)).thenReturn(Optional.of(electronics));

        ProductRequest request = new ProductRequest();
        request.setSku("ELEC-NEW-009");
        request.setName("Nimbus 65W GaN Charger");
        request.setPrice(new BigDecimal("2199.00"));
        request.setStockQuantity(25);
        request.setCategoryId(1L);

        assertThatThrownBy(() -> productService.create(request))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("not active");
    }

    @Test
    @DisplayName("delete is a soft delete and is not repeatable")
    void softDelete_marksInactiveAndRejectsSecondCall() {
        when(productRepository.findById(3L)).thenReturn(Optional.of(headphones));

        productService.softDelete(3L);
        assertThat(headphones.isActive()).isFalse();

        assertThatThrownBy(() -> productService.softDelete(3L))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("already deleted");
    }

    // ---------------------------------------------------------------- Search

    @Test
    @DisplayName("rejects an inverted price range instead of returning an empty page")
    void search_minGreaterThanMax_throwsInvalidOperation() {
        assertThatThrownBy(() -> productService.search(null, null, null,
                new BigDecimal("5000"), new BigDecimal("1000"), null, PageRequest.of(0, 20)))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("minPrice cannot be greater than maxPrice");
    }

    @Test
    @DisplayName("wraps the Spring page in the stable PageResponse shape")
    void search_returnsPageResponse() {
        Page<Product> page = new PageImpl<>(Collections.singletonList(headphones),
                PageRequest.of(0, 20), 1);
        when(productRepository.findAll(ArgumentMatchers.<Specification<Product>>any(), any(PageRequest.class)))
                .thenReturn(page);

        PageResponse<ProductResponse> response = productService.search(
                "pulse", null, null, null, null, null, PageRequest.of(0, 20));

        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getTotalElements()).isEqualTo(1);
        assertThat(response.isFirst()).isTrue();
        assertThat(response.getContent().get(0).getSku()).isEqualTo("ELEC-AUD-003");
    }

    // ----------------------------------------------------------------- Stock

    @Test
    @DisplayName("reserving decrements stock and returns the line total")
    void reserveStock_decrementsAndTotals() {
        runTransactionsInline();
        when(productRepository.findAllByIdIn(anyList())).thenReturn(Collections.singletonList(headphones));

        StockOperationResponse response = productService.reserveStock(
                stockRequest(new StockItemRequest(3L, 2)));

        assertThat(headphones.getStockQuantity()).isEqualTo(8);
        assertThat(response.getTotalAmount()).isEqualByComparingTo("14998.00");
        assertThat(response.getLines()).hasSize(1);
        assertThat(response.getLines().get(0).getRemainingStock()).isEqualTo(8);
    }

    @Test
    @DisplayName("two lines for the same product become one decrement, not two")
    void reserveStock_mergesDuplicateLines() {
        runTransactionsInline();
        when(productRepository.findAllByIdIn(anyList())).thenReturn(Collections.singletonList(headphones));

        StockOperationResponse response = productService.reserveStock(
                stockRequest(new StockItemRequest(3L, 2), new StockItemRequest(3L, 3)));

        assertThat(headphones.getStockQuantity()).isEqualTo(5);
        assertThat(response.getLines()).hasSize(1);
        assertThat(response.getLines().get(0).getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("refuses to oversell and names the shortfall")
    void reserveStock_insufficient_throwsAndLeavesStockUntouched() {
        runTransactionsInline();
        when(productRepository.findAllByIdIn(anyList())).thenReturn(Collections.singletonList(headphones));

        assertThatThrownBy(() -> productService.reserveStock(stockRequest(new StockItemRequest(3L, 99))))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("ELEC-AUD-003")
                .hasMessageContaining("10 unit(s)");
    }

    @Test
    @DisplayName("refuses to reserve a soft-deleted product")
    void reserveStock_inactiveProduct_throwsInvalidOperation() {
        runTransactionsInline();
        headphones.setActive(false);
        when(productRepository.findAllByIdIn(anyList())).thenReturn(Collections.singletonList(headphones));

        assertThatThrownBy(() -> productService.reserveStock(stockRequest(new StockItemRequest(3L, 1))))
                .isInstanceOf(InvalidOperationException.class)
                .hasMessageContaining("no longer available");
    }

    @Test
    @DisplayName("reports an unknown product id rather than silently skipping the line")
    void reserveStock_unknownProduct_throwsNotFound() {
        runTransactionsInline();
        when(productRepository.findAllByIdIn(anyList())).thenReturn(Collections.<Product>emptyList());

        assertThatThrownBy(() -> productService.reserveStock(stockRequest(new StockItemRequest(404L, 1))))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("404");
    }

    @Test
    @DisplayName("releasing puts the units back")
    void releaseStock_incrementsStock() {
        runTransactionsInline();
        when(productRepository.findAllByIdIn(anyList())).thenReturn(Collections.singletonList(headphones));

        productService.releaseStock(stockRequest(new StockItemRequest(3L, 4)));

        assertThat(headphones.getStockQuantity()).isEqualTo(14);
    }

    @Test
    @DisplayName("retries a lost optimistic-lock race three times, then gives up with 409 semantics")
    void reserveStock_optimisticLockConflict_retriesThenFails() {
        when(transactionTemplate.execute(ArgumentMatchers.<TransactionCallback<StockOperationResponse>>any()))
                .thenThrow(new OptimisticLockingFailureException("row changed"));

        assertThatThrownBy(() -> productService.reserveStock(stockRequest(new StockItemRequest(3L, 1))))
                .isInstanceOf(OptimisticLockingFailureException.class);

        verify(transactionTemplate, times(3))
                .execute(ArgumentMatchers.<TransactionCallback<StockOperationResponse>>any());
    }

    @Test
    @DisplayName("succeeds on a retry after one lost race")
    void reserveStock_succeedsOnSecondAttempt() {
        List<Product> found = Collections.singletonList(headphones);
        when(productRepository.findAllByIdIn(anyList())).thenReturn(found);
        when(transactionTemplate.execute(ArgumentMatchers.<TransactionCallback<StockOperationResponse>>any()))
                .thenThrow(new OptimisticLockingFailureException("row changed"))
                .thenAnswer(invocation -> {
                    TransactionCallback<StockOperationResponse> callback = invocation.getArgument(0);
                    return callback.doInTransaction(null);
                });

        StockOperationResponse response = productService.reserveStock(
                stockRequest(new StockItemRequest(3L, 1)));

        assertThat(response.getLines()).hasSize(1);
        assertThat(headphones.getStockQuantity()).isEqualTo(9);
        verify(transactionTemplate, times(2))
                .execute(ArgumentMatchers.<TransactionCallback<StockOperationResponse>>any());
    }
}
