package com.oms.order.client.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class StockOperationRequest {

    private String orderReference;
    private List<StockItemRequest> items;
}
