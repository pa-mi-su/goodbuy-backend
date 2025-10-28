package app.goodbuy.products;

import java.util.List;

/**
 * Stable contract sent to the iOS app.
 * Keep this shape backward-compatible.
 */
public record ProductDto(
        String gtin,
        String name,
        String brand,
        String category,        // e.g. "cleaner"
        List<String> images,    // absolute URLs
        List<String> ingredients,
        List<String> claims,
        List<String> hazards
) {}
