package com.oms.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CancelOrderRequest {

    @Schema(example = "Changed my mind, no longer needed")
    private String reason;
}
