package com.oms.order.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
public class StockOperationResponse {

    private String orderReference;
    private BigDecimal totalAmount;
    private List<StockLineResponse> lines;
}
