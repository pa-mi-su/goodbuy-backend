package app.goodbuy.catalog;

import app.goodbuy.products.ProductDto;

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
    Optional<ProductDto> findByGtin(String gtin14) throws CatalogTransportException;
}
