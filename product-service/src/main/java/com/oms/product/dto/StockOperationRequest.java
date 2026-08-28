package com.oms.product.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.Valid;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
public class StockOperationRequest {

    /** The order number, carried through so the log line ties back to an order. */
    @NotNull(message = "Order reference is required")
    @Size(max = 40, message = "Order reference must not exceed 40 characters")
    private String orderReference;

    @Valid
    @NotEmpty(message = "At least one line item is required")
    private List<StockItemRequest> items;
}
