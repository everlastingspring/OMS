package com.oms.product.specification;

import com.oms.product.entity.Product;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

import javax.persistence.criteria.Predicate;
import java.math.BigDecimal;

/**
 * Search filters composed with Specification.and(). Each returns null when its
 * input is absent, and Spring Data drops null specifications, so the caller can
 * chain every filter unconditionally instead of branching.
 */
public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> keywordMatches(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return null;
        }
        String pattern = "%" + keyword.trim().toLowerCase() + "%";
        return (root, query, cb) -> {
            Predicate byName = cb.like(cb.lower(root.get("name")), pattern);
            Predicate bySku = cb.like(cb.lower(root.get("sku")), pattern);
            Predicate byDescription = cb.like(cb.lower(root.get("description")), pattern);
            return cb.or(byName, bySku, byDescription);
        };
    }

    public static Specification<Product> inCategory(Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    public static Specification<Product> categoryNamed(String categoryName) {
        if (!StringUtils.hasText(categoryName)) {
            return null;
        }
        return (root, query, cb) ->
                cb.equal(cb.lower(root.get("category").get("name")), categoryName.trim().toLowerCase());
    }

    public static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        if (minPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    public static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return (root, query, cb) -> cb.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    public static Specification<Product> inStockOnly(Boolean inStock) {
        if (inStock == null || !inStock) {
            return null;
        }
        return (root, query, cb) -> cb.greaterThan(root.get("stockQuantity"), 0);
    }

    /** Soft-deleted products are invisible to every public search. */
    public static Specification<Product> activeOnly() {
        return (root, query, cb) -> cb.isTrue(root.get("active"));
    }
}
