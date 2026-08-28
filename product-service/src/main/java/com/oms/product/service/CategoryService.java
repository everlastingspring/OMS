package com.oms.product.service;

import com.oms.product.dto.CategoryRequest;
import com.oms.product.dto.CategoryResponse;

import java.util.List;

public interface CategoryService {

    List<CategoryResponse> getAll();

    CategoryResponse getById(Long id);

    CategoryResponse create(CategoryRequest request);

    CategoryResponse update(Long id, CategoryRequest request);

    void deactivate(Long id);
}
