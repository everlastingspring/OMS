package com.oms.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockLineResponse {
    private Long productId;
    private String sku;
    private String productName;
    private Integer quantity;
    private BigDecimal unitPrice;
    private Integer remainingStock;
}
