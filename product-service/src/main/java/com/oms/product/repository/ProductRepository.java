package com.oms.product.repository;

import com.oms.product.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    Optional<Product> findBySku(String sku);

    boolean existsBySku(String sku);

    Optional<Product> findByIdAndActiveTrue(Long id);

    /**
     * Loads exactly the products a stock operation touches, in one query.
     * The caller then mutates them inside the same transaction, so the
     * @Version check applies at flush.
     */
    @Query("SELECT p FROM Product p WHERE p.id IN :ids")
    List<Product> findAllByIdIn(@Param("ids") List<Long> ids);

    long countByCategoryIdAndActiveTrue(Long categoryId);
}
