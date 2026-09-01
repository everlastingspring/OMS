package com.oms.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

/** SKU is absent on purpose: it is the catalogue's stable identity and never changes. */
@Getter
@Setter
@NoArgsConstructor
public class ProductUpdateRequest {

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 200, message = "Product name must be between 2 and 200 characters")
    @Schema(example = "Aurora 5G Smartphone 256GB")
    private String name;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    @Schema(example = "6.5 inch AMOLED, 5000 mAh battery, upgraded 256GB storage")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Price may have at most 2 decimal places")
    @Schema(example = "27999.00")
    private BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Schema(example = "40")
    private Integer stockQuantity;

    @NotNull(message = "Category id is required")
    @Schema(example = "1", description = "1=Electronics, 2=Home & Kitchen, 3=Books, 4=Sports & Fitness")
    private Long categoryId;
}
