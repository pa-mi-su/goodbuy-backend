package app.goodbuy.catalog;

import app.goodbuy.products.ProductDto;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * EAN-DB-backed implementation.
 * This version is a placeholder: it validates config and returns empty.
 * Next step we’ll add the actual HTTPS call + mapping.
 */
public class EanDbCatalogClient implements ExternalCatalogClient {

    private final String baseUrl;
    private final String jwt;

    public EanDbCatalogClient(String baseUrl, String jwt) {
        this.baseUrl = Objects.requireNonNull(baseUrl, "baseUrl");
        this.jwt = Objects.requireNonNull(jwt, "jwt");
    }

    @Override
    public Optional<ProductDto> findByGtin(String gtin14) {
        // Stub: no network yet. Just prove wiring works.
        // Return empty to indicate "not found" until we implement HTTP in the next step.
        return Optional.empty();
    }

    // --- Helper we’ll use next step to map EAN-DB response → ProductDto ---
    // Keeping it here to show intended mapping shape.
    @SuppressWarnings("unused")
    private static ProductDto mapToDto(String gtin14,
                                       String name,
                                       String brand,
                                       String category,
                                       List<String> images,
                                       List<String> ingredients,
                                       List<String> claims,
                                       List<String> hazards) {
        return new ProductDto(
                gtin14,
                name,
                brand,
                category,
                images,
                ingredients,
                claims,
                hazards
        );
    }
}
