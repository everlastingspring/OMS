package com.oms.product.repository;

import com.oms.product.config.JpaAuditingConfig;
import com.oms.product.entity.Category;
import com.oms.product.entity.Product;
import com.oms.product.specification.ProductSpecifications;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(JpaAuditingConfig.class)
@ActiveProfiles("test")
@DisplayName("ProductRepository")
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Category electronics;
    private Category books;

    private Product product(String sku, String name, String price, int stock, Category category, boolean active) {
        Product product = new Product();
        product.setSku(sku);
        product.setName(name);
        product.setDescription(name + " description");
        product.setPrice(new BigDecimal(price));
        product.setStockQuantity(stock);
        product.setCategory(category);
        product.setActive(active);
        return product;
    }

    private Category category(String name) {
        Category category = new Category();
        category.setName(name);
        category.setActive(true);
        return category;
    }

    @BeforeEach
    void seed() {
        electronics = categoryRepository.saveAndFlush(category("Electronics"));
        books = categoryRepository.saveAndFlush(category("Books"));

        productRepository.saveAndFlush(
                product("ELEC-AUD-003", "Pulse ANC Wireless Headphones", "7499.00", 120, electronics, true));
        productRepository.saveAndFlush(
                product("ELEC-PHN-001", "Aurora 5G Smartphone", "24999.00", 40, electronics, true));
        productRepository.saveAndFlush(
                product("BOOK-TEC-002", "Effective Java", "999.00", 150, books, true));
        productRepository.saveAndFlush(
                product("ELEC-ACC-004", "Nimbus 65W GaN Charger", "2199.00", 0, electronics, true));
        productRepository.saveAndFlush(
                product("HOME-KTC-003", "Retired Cookware Set", "4599.00", 10, books, false));
    }

    @Test
    @DisplayName("SKU uniqueness is enforced by the database")
    void duplicateSku_violatesUniqueConstraint() {
        assertThatThrownBy(() -> productRepository.saveAndFlush(
                product("ELEC-AUD-003", "Copycat", "1.00", 1, electronics, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("finds by SKU exactly")
    void findBySku_returnsProduct() {
        assertThat(productRepository.findBySku("ELEC-PHN-001"))
                .isPresent()
                .get()
                .extracting(Product::getName)
                .isEqualTo("Aurora 5G Smartphone");
    }

    @Test
    @DisplayName("soft-deleted products never appear in an active-only search")
    void search_excludesSoftDeleted() {
        Page<Product> page = productRepository.findAll(
                Specification.where(ProductSpecifications.activeOnly()), PageRequest.of(0, 20));

        assertThat(page.getTotalElements()).isEqualTo(4);
        assertThat(page.getContent()).extracting(Product::getSku).doesNotContain("HOME-KTC-003");
    }

    @Test
    @DisplayName("keyword matches on name, SKU and description, case-insensitively")
    void search_keywordMatchesNameSkuAndDescription() {
        Specification<Product> byName = Specification.where(ProductSpecifications.activeOnly())
                .and(ProductSpecifications.keywordMatches("PULSE"));
        Specification<Product> bySku = Specification.where(ProductSpecifications.activeOnly())
                .and(ProductSpecifications.keywordMatches("elec-phn"));

        assertThat(productRepository.findAll(byName, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
        assertThat(productRepository.findAll(bySku, PageRequest.of(0, 20)).getTotalElements()).isEqualTo(1);
    }

    @Test
    @DisplayName("category and price filters combine with AND")
    void search_categoryAndPriceRangeCombine() {
        Specification<Product> spec = Specification.where(ProductSpecifications.activeOnly())
                .and(ProductSpecifications.inCategory(electronics.getId()))
                .and(ProductSpecifications.priceAtLeast(new BigDecimal("2000")))
                .and(ProductSpecifications.priceAtMost(new BigDecimal("10000")));

        Page<Product> page = productRepository.findAll(spec, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getSku)
                .containsExactlyInAnyOrder("ELEC-AUD-003", "ELEC-ACC-004");
    }

    @Test
    @DisplayName("inStock filter hides zero-stock products")
    void search_inStockOnlyHidesZeroStock() {
        Specification<Product> spec = Specification.where(ProductSpecifications.activeOnly())
                .and(ProductSpecifications.inStockOnly(true));

        Page<Product> page = productRepository.findAll(spec, PageRequest.of(0, 20));

        assertThat(page.getContent()).extracting(Product::getSku).doesNotContain("ELEC-ACC-004");
        assertThat(page.getTotalElements()).isEqualTo(3);
    }

    @Test
    @DisplayName("results are sortable by price")
    void search_sortsByPriceAscending() {
        Page<Product> page = productRepository.findAll(
                Specification.where(ProductSpecifications.activeOnly()),
                PageRequest.of(0, 20, Sort.by(Sort.Direction.ASC, "price")));

        assertThat(page.getContent()).extracting(Product::getSku)
                .containsExactly("BOOK-TEC-002", "ELEC-ACC-004", "ELEC-AUD-003", "ELEC-PHN-001");
    }

    @Test
    @DisplayName("counts only active products in a category, which gates category deletion")
    void countByCategoryIdAndActiveTrue_ignoresSoftDeleted() {
        assertThat(productRepository.countByCategoryIdAndActiveTrue(electronics.getId())).isEqualTo(3);
        assertThat(productRepository.countByCategoryIdAndActiveTrue(books.getId())).isEqualTo(1);
    }

    @Test
    @DisplayName("@Version increments on every update, which is what blocks a lost update")
    void version_incrementsOnUpdate() {
        Product product = productRepository.findBySku("ELEC-AUD-003").orElseThrow(IllegalStateException::new);
        Long initialVersion = product.getVersion();

        product.reserve(5);
        productRepository.saveAndFlush(product);
        entityManager.clear();

        Product reloaded = productRepository.findBySku("ELEC-AUD-003").orElseThrow(IllegalStateException::new);

        assertThat(reloaded.getStockQuantity()).isEqualTo(115);
        assertThat(reloaded.getVersion()).isGreaterThan(initialVersion);
    }
}
