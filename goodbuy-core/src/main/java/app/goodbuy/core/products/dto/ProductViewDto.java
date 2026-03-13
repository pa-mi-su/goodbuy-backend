package app.goodbuy.core.products.dto;

import java.math.BigDecimal;
import java.util.List;

public record ProductViewDto(
        String gtin,
        String name,
        String brand,
        String category,
        String domain,
        boolean categorySupported,
        String primaryImageUrl,
        List<String> images,
        List<ProductIngredientViewDto> ingredients,
        List<String> claims,
        List<String> hazards,
        List<ProductGuidanceSignalDto> guidanceSignals,
        String guidanceConfidence,
        String source,
        BigDecimal safetyScore,   // product-level score
        String ratingLetter       // product-level grade
) {
    public record ProductGuidanceSignalDto(
            String label,
            String tone,
            String basis
    ) {}
}
