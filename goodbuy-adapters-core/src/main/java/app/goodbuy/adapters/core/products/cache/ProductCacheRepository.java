package app.goodbuy.adapters.core.products.cache;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for the product cache table.
 *
 * We keep this minimal:
 *  - findById(gtin) is inherited from JpaRepository
 *  - save(entity) is also inherited
 */
public interface ProductCacheRepository extends JpaRepository<ProductCacheEntity, String> {
    // No extra methods for now.
}
