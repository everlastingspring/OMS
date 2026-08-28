package com.oms.product.service.impl;

import com.oms.common.exception.DuplicateResourceException;
import com.oms.common.exception.InvalidOperationException;
import com.oms.common.exception.ResourceNotFoundException;
import com.oms.product.dto.CategoryRequest;
import com.oms.product.dto.CategoryResponse;
import com.oms.product.entity.Category;
import com.oms.product.mapper.ProductMapper;
import com.oms.product.repository.CategoryRepository;
import com.oms.product.repository.ProductRepository;
import com.oms.product.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getAll() {
        return ProductMapper.toCategoryResponses(categoryRepository.findByActiveTrueOrderByNameAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(Long id) {
        return ProductMapper.toResponse(requireCategory(id));
    }

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.getName().trim();
        if (categoryRepository.existsByNameIgnoreCase(name)) {
            throw new DuplicateResourceException("Category", "name", name);
        }
        Category category = new Category();
        category.setName(name);
        category.setDescription(request.getDescription());
        category.setActive(true);
        Category saved = categoryRepository.save(category);
        log.info("Created category id={} name={}", saved.getId(), saved.getName());
        return ProductMapper.toResponse(saved);
    }

    @Override
    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = requireCategory(id);
        String name = request.getName().trim();
        categoryRepository.findByNameIgnoreCase(name)
                .filter(other -> !other.getId().equals(id))
                .ifPresent(other -> {
                    throw new DuplicateResourceException("Category", "name", name);
                });
        category.setName(name);
        category.setDescription(request.getDescription());
        log.info("Updated category id={}", id);
        return ProductMapper.toResponse(category);
    }

    @Override
    @Transactional
    public void deactivate(Long id) {
        Category category = requireCategory(id);
        long activeProducts = productRepository.countByCategoryIdAndActiveTrue(id);
        if (activeProducts > 0) {
            throw new InvalidOperationException(String.format(
                    "Category '%s' still has %d active product(s). Move or delete them first.",
                    category.getName(), activeProducts));
        }
        category.setActive(false);
        log.info("Deactivated category id={}", id);
    }

    private Category requireCategory(Long id) {
        return categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Category", "id", id));
    }
}
