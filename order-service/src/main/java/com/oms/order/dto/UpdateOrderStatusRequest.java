package com.oms.order.dto;

import com.oms.order.entity.OrderStatus;
import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotNull;

@Getter
@Setter
public class UpdateOrderStatusRequest {

    @NotNull(message = "status is required")
    private OrderStatus status;
}
