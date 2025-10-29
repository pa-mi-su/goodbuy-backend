package app.goodbuy.catalog;

import app.goodbuy.products.ProductDto;

/**
 * Minimal contract for any external barcode catalog (EAN-DB, UPCitemdb, etc.).
 * Returning null means "not found".
 * Implementations MUST normalize inputs to GTIN-14 before calling the remote API.
 */
public interface ExternalCatalogClient {
    ProductDto lookupByGtin14(String gtin14) throws Exception;
}
