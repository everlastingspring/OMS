package com.oms.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

@Configuration
public class Resilience4jConfig {

    private CircuitBreakerConfig cbConfig() {
        return CircuitBreakerConfig.custom()
                .slidingWindowSize(5)
                .failureRateThreshold(50f)
                .waitDurationInOpenState(Duration.ofSeconds(10))
                .permittedNumberOfCallsInHalfOpenState(2)
                .build();
    }

    private RetryConfig retryConfig() {
        return RetryConfig.custom()
                .maxAttempts(3)
                .waitDuration(Duration.ofMillis(200))
                .build();
    }

    @Bean
    public CircuitBreaker userServiceCircuitBreaker() {
        return CircuitBreaker.of("user-service", cbConfig());
    }

    @Bean
    public Retry userServiceRetry() {
        return Retry.of("user-service", retryConfig());
    }

    @Bean
    public CircuitBreaker productServiceCircuitBreaker() {
        return CircuitBreaker.of("product-service", cbConfig());
    }

    @Bean
    public Retry productServiceRetry() {
        return Retry.of("product-service", retryConfig());
    }
}
