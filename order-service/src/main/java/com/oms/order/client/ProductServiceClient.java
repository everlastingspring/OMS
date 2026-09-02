package com.oms.order.client;

import com.oms.common.dto.ApiResponse;
import com.oms.common.exception.BusinessException;
import com.oms.common.exception.ServiceUnavailableException;
import com.oms.order.client.dto.StockOperationRequest;
import com.oms.order.client.dto.StockOperationResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class ProductServiceClient {

    private final RestTemplate internalRestTemplate;
    private final CircuitBreaker productServiceCircuitBreaker;
    private final Retry productServiceRetry;

    @Value("${oms.services.product-service-url}")
    private String productServiceUrl;

    public StockOperationResponse reserveStock(StockOperationRequest request) {
        try {
            return productServiceCircuitBreaker.executeCallable(
                    () -> productServiceRetry.executeCallable(
                            () -> doReserveStock(request)));
        } catch (Exception ex) {
            log.error("reserve-stock failed for order {}: {}", request.getOrderReference(), ex.getMessage());
            if (ex instanceof BusinessException) {
                throw (BusinessException) ex;
            }
            throw new ServiceUnavailableException("product-service");
        }
    }

    public StockOperationResponse releaseStock(StockOperationRequest request) {
        try {
            String url = productServiceUrl + "/api/v1/internal/products/release-stock";
            ResponseEntity<ApiResponse<StockOperationResponse>> response = internalRestTemplate.exchange(
                    url, HttpMethod.POST, new HttpEntity<>(request),
                    new ParameterizedTypeReference<ApiResponse<StockOperationResponse>>() {});
            return response.getBody().getData();
        } catch (Exception ex) {
            log.warn("Compensating release-stock failed for order {}: {}",
                    request.getOrderReference(), ex.getMessage());
            return null;
        }
    }

    private StockOperationResponse doReserveStock(StockOperationRequest request) {
        String url = productServiceUrl + "/api/v1/internal/products/reserve-stock";
        ResponseEntity<ApiResponse<StockOperationResponse>> response = internalRestTemplate.exchange(
                url, HttpMethod.POST, new HttpEntity<>(request),
                new ParameterizedTypeReference<ApiResponse<StockOperationResponse>>() {});
        return response.getBody().getData();
    }
}
