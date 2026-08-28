package com.oms.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class StockItemRequest {

    private Long productId;
    private int quantity;
}
