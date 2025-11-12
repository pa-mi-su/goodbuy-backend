package app.goodbuy.core.products.port;

import app.goodbuy.core.products.dto.ProductDetailDto;

import java.util.Optional;

/**
 * Port for caching product lookups in our own database.
 *
 * The API layer (via ProductService) will:
 *   1. Query this port to check for an existing product by GTIN.
 *   2. If found, return it immediately.
 *   3. If not found, fetch from external catalog and then call save().
 */
public interface ProductCachePort {

    /**
     * Find a cached product by its GTIN-14 code.
     * @param gtin14 normalized 14-digit GTIN.
     * @return Optional containing the cached product, or empty if not present.
     */
    Optional<ProductDetailDto> findByGtin(String gtin14);

    /**
     * Persist a product into the local cache.
     * Implementations may upsert based on GTIN.
     * @param dto product data to store
     * @return the saved ProductDetailDto (may include DB-generated fields)
     */
    ProductDetailDto save(ProductDetailDto dto);
}
