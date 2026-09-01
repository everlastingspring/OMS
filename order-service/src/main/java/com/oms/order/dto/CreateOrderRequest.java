package com.oms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotEmpty;
import java.util.List;

@Getter
@Setter
public class CreateOrderRequest {

    @NotEmpty(message = "Order must have at least one item")
    @Valid
    private List<OrderItemRequest> items;

    @NotBlank(message = "shippingAddress is required")
    @Schema(example = "123 Main Street, Bangalore, Karnataka 560001")
    private String shippingAddress;

    @Schema(example = "Please deliver between 9am-6pm")
    private String notes;
}
