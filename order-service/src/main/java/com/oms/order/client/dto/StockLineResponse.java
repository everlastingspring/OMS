package com.oms.order.client.dto;

import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
public class StockLineResponse {

    private Long productId;
    private String sku;
    private String productName;
    private int quantity;
    private BigDecimal unitPrice;
    private int remainingStock;
}
