package app.goodbuy.core.products.util;

import app.goodbuy.core.products.dto.ProductDetailDto;
import java.util.*;
import java.util.stream.Collectors;

public final class IngredientMerger {

    private IngredientMerger() {}

    /**
     * Merge GoodBuy-DB ingredients + external ingredients.
     * Rules:
     * 1. If DB has an ingredient → it wins (external never overwrites our curated canonical/rating data)
     * 2. If external has an ingredient DB does NOT → include it as “external only”
     * 3. Preserve ingredient order from the external list
     */
    public static List<ProductDetailDto.IngredientDto> merge(
            List<ProductDetailDto.IngredientDto> dbIngredients,
            List<ProductDetailDto.IngredientDto> externalIngredients
    ) {
        if (externalIngredients == null || externalIngredients.isEmpty()) {
            return dbIngredients != null ? dbIngredients : List.of();
        }

        if (dbIngredients == null) {
            return externalIngredients;
        }

        // Map DB ingredients by canonical key
        Map<String, ProductDetailDto.IngredientDto> dbMap =
                dbIngredients.stream()
                        .collect(Collectors.toMap(
                                i -> normalizeKey(i.canonical()),
                                i -> i
                        ));

        List<ProductDetailDto.IngredientDto> merged = new ArrayList<>();

        for (ProductDetailDto.IngredientDto ext : externalIngredients) {
            String key = normalizeKey(ext.canonical());

            if (dbMap.containsKey(key)) {
                // Use DB-curated ingredient
                merged.add(dbMap.get(key));
            } else {
                // Use external ingredient
                merged.add(ext);
            }
        }

        return merged;
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase();
    }
}
