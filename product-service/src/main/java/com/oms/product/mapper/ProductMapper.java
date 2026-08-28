package com.oms.product.mapper;

import com.oms.product.dto.CategoryResponse;
import com.oms.product.dto.ProductResponse;
import com.oms.product.entity.Category;
import com.oms.product.entity.Product;

import java.util.List;
import java.util.stream.Collectors;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse toResponse(Product product) {
        ProductResponse response = new ProductResponse();
        response.setId(product.getId());
        response.setSku(product.getSku());
        response.setName(product.getName());
        response.setDescription(product.getDescription());
        response.setPrice(product.getPrice());
        response.setStockQuantity(product.getStockQuantity());
        response.setInStock(product.getStockQuantity() != null && product.getStockQuantity() > 0);
        response.setActive(product.isActive());
        response.setCreatedAt(product.getCreatedAt());
        response.setUpdatedAt(product.getUpdatedAt());
        Category category = product.getCategory();
        if (category != null) {
            response.setCategoryId(category.getId());
            response.setCategoryName(category.getName());
        }
        return response;
    }

    public static CategoryResponse toResponse(Category category) {
        CategoryResponse response = new CategoryResponse();
        response.setId(category.getId());
        response.setName(category.getName());
        response.setDescription(category.getDescription());
        response.setActive(category.isActive());
        return response;
    }

    public static List<CategoryResponse> toCategoryResponses(List<Category> categories) {
        return categories.stream().map(ProductMapper::toResponse).collect(Collectors.toList());
    }
}
