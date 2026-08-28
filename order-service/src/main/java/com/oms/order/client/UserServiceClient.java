package com.oms.order.client;

import com.oms.common.dto.ApiResponse;
import com.oms.common.exception.ResourceNotFoundException;
import com.oms.order.client.dto.InternalUserResponse;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
@RequiredArgsConstructor
public class UserServiceClient {

    private final RestTemplate internalRestTemplate;
    private final CircuitBreaker userServiceCircuitBreaker;
    private final Retry userServiceRetry;

    @Value("${oms.services.user-service-url}")
    private String userServiceUrl;

    public InternalUserResponse resolveUser(Long userId) {
        try {
            return userServiceCircuitBreaker.executeCallable(
                    () -> userServiceRetry.executeCallable(
                            () -> doResolveUser(userId)));
        } catch (Exception ex) {
            log.warn("user-service unavailable for userId={}: {}", userId, ex.getMessage());
            throw new ResourceNotFoundException("User", "id", userId);
        }
    }

    private InternalUserResponse doResolveUser(Long userId) {
        String url = userServiceUrl + "/api/v1/internal/users/" + userId;
        ResponseEntity<ApiResponse<InternalUserResponse>> response = internalRestTemplate.exchange(
                url, HttpMethod.GET, null,
                new ParameterizedTypeReference<ApiResponse<InternalUserResponse>>() {});
        return response.getBody().getData();
    }
}
