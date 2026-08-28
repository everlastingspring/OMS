package com.oms.product.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class StockOperationResponse {
    private String orderReference;
    private BigDecimal totalAmount;
    private List<StockLineResponse> lines;
}
