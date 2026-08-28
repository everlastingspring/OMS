package com.oms.product.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class CategoryResponse {
    private Long id;
    private String name;
    private String description;
    private boolean active;
}
