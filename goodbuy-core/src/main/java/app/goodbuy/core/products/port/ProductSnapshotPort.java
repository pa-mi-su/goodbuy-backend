package app.goodbuy.core.products.port;

import app.goodbuy.core.products.dto.ProductDetailDto;

/**
 * Port responsible for persisting a structured snapshot of:
 *   - product (by GTIN)
 *   - its ingredient list
 * into our normalized schema (e.g. product, product_ingredient tables).
 *
 * Called by ProductCacheAdapter AFTER a successful external catalog hit.
 * Any implementation errors should be considered non-fatal at the adapter level.
 */
public interface ProductSnapshotPort {

    /**
     * Upsert a product row and its associated ingredients based on the DTO.
     * Implementations should:
     *   - upsert product by GTIN/primary key
     *   - replace or upsert its ingredient association rows
     */
    void saveSnapshot(ProductDetailDto dto);
}
