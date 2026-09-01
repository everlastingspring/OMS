package com.oms.product.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import io.swagger.v3.oas.annotations.media.Schema;

import javax.validation.constraints.DecimalMin;
import javax.validation.constraints.Digits;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
public class ProductRequest {

    @NotBlank(message = "SKU is required")
    @Pattern(regexp = "^[A-Z0-9\\-]{3,50}$",
            message = "SKU must be 3-50 characters of uppercase letters, digits or hyphens")
    @Schema(example = "ELEC-TWG-001")
    private String sku;

    @NotBlank(message = "Product name is required")
    @Size(min = 2, max = 200, message = "Product name must be between 2 and 200 characters")
    @Schema(example = "Test Widget Pro")
    private String name;

    @Size(max = 5000, message = "Description must not exceed 5000 characters")
    @Schema(example = "High quality test widget with advanced features")
    private String description;

    @NotNull(message = "Price is required")
    @DecimalMin(value = "0.01", message = "Price must be greater than zero")
    @Digits(integer = 10, fraction = 2, message = "Price may have at most 2 decimal places")
    @Schema(example = "999.99")
    private BigDecimal price;

    @NotNull(message = "Stock quantity is required")
    @Min(value = 0, message = "Stock quantity cannot be negative")
    @Schema(example = "50")
    private Integer stockQuantity;

    @NotNull(message = "Category id is required")
    @Schema(example = "1", description = "1=Electronics, 2=Home & Kitchen, 3=Books, 4=Sports & Fitness")
    private Long categoryId;
}
