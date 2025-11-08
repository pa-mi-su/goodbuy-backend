package app.goodbuy.adapters.catalog;

import app.goodbuy.core.products.dto.ProductDetailDto;

import java.util.Optional;

/**
 * Abstraction for any external product catalog.
 * Input must be a normalized GTIN-14 string.
 */
public interface ExternalCatalogClient {
    /**
     * Finds a product by GTIN-14.
     * @param gtin14 normalized 14-digit GTIN
     * @return Optional with ProductDto if found, otherwise empty.
     * @throws CatalogTransportException for HTTP/timeouts/decoding issues.
     */
    Optional<ProductDetailDto> findByGtin(String gtin14) throws CatalogTransportException;
}
