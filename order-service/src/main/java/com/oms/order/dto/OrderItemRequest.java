package com.oms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

@Getter
@Setter
public class OrderItemRequest {

    @NotNull(message = "productId is required")
    @Schema(example = "1", description = "Product ID from product-service (1=Aurora 5G Smartphone)")
    private Long productId;

    @Min(value = 1, message = "quantity must be at least 1")
    @Schema(example = "2")
    private int quantity;
}
