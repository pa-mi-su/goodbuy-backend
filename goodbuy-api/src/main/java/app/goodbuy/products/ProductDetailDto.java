package app.goodbuy.products;

import java.util.List;
import java.util.Map;

public record ProductDetailDto(
        String gtin,
        String name,
        String brand,
        String category,
        String description,
        List<ImageDto> images,
        List<IngredientDto> ingredients,
        Map<String, String> titles,          // e.g., { "en": "Name", "fr": "Nom" }
        Map<String, String> manufacturer,    // e.g., { "id": "mrs-meyer-s-s-c-johnson", "en": "Mrs Meyer's" }
        String source                         // "EAN-DB", etc.
) {
    public record ImageDto(
            String url,
            Integer width,
            Integer height
    ) {}

    public record IngredientDto(
            String id,                        // e.g., "e330" or "lavender-oil"
            String original,                  // e.g., "Citric Acid"
            String canonical,                 // e.g., "Citric Acid (E330)"
            Map<String, String> externalIds,  // e.g., { "cosIng": "32858", "wikidata": "Q159683" }
            Boolean isVegan,
            Boolean isVegetarian
    ) {}
}
