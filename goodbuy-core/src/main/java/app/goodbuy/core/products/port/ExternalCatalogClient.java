package app.goodbuy.core.products.port;

import app.goodbuy.core.products.dto.ProductDetailDto;

import java.util.Optional;

/**
 * Port for looking up product details by GTIN-14 from an external catalog.
 * Implemented by adapters like EanDbCatalogClient, EanSearchClient, etc.
 */
public interface ExternalCatalogClient {

    /**
     * Lookup a product by normalized 14-digit GTIN.
     *
     * @param gtin14 normalized GTIN-14 (caller is responsible for normalization)
     * @return Optional with ProductDetailDto if found; empty if not found or on handled transport issues.
     */
    Optional<ProductDetailDto> findByGtin(String gtin14);
}
