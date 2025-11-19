package app.goodbuy.core.products.port;

import app.goodbuy.core.products.dto.ProductDetailDto;

import java.util.Optional;

/**
 * Port for reading structured product data from GoodBuy's own database:
 *   - products table
 *   - product_ingredients table
 *   - ingredients table
 *
 * Implementations should:
 *   - load a product by GTIN/EAN
 *   - resolve its ingredient links
 *   - map the graph into ProductDetailDto
 */
public interface ProductLookupPort {

    /**
     * Find a GoodBuy product by its GTIN-14 code.
     *
     * @param gtin14 normalized 14-digit GTIN/EAN
     * @return Optional containing a fully built ProductDetailDto
     *         (product + ingredients), or empty if not present.
     */
    Optional<ProductDetailDto> findByGtin(String gtin14);
}
