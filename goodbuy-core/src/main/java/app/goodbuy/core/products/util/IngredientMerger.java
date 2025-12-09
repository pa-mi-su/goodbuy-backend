package app.goodbuy.core.products.util;

import app.goodbuy.core.products.dto.ProductDetailDto.IngredientDto;

import java.util.*;

/**
 * Merge GoodBuy-DB ingredients + external ingredients.
 *
 * Rules:
 *  1. If DB has an ingredient → it wins (external never overwrites our curated data)
 *  2. If external has an ingredient DB does NOT → include it as “external only”
 *  3. Preserve ingredient order from the external list
 */
public final class IngredientMerger {

    private IngredientMerger() {
        // utility
    }

    public static List<IngredientDto> merge(
            List<IngredientDto> dbIngredients,
            List<IngredientDto> externalIngredients
    ) {
        // No external → just return DB (or empty)
        if (externalIngredients == null || externalIngredients.isEmpty()) {
            return dbIngredients != null ? dbIngredients : List.of();
        }

        // No DB → just return external
        if (dbIngredients == null || dbIngredients.isEmpty()) {
            return externalIngredients;
        }

        // Build a lookup map from DB ingredients using a normalized canonical key.
        // If there are duplicates, the FIRST one wins.
        Map<String, IngredientDto> dbByCanonical = new LinkedHashMap<>();
        for (IngredientDto dbIng : dbIngredients) {
            String key = normalizeKey(dbIng.canonical());
            if (key.isEmpty()) {
                // If canonical is missing, you could optionally use id or original as a fallback:
                key = normalizeKey(dbIng.id());
                if (key.isEmpty()) {
                    key = normalizeKey(dbIng.original());
                }
            }
            if (!key.isEmpty() && !dbByCanonical.containsKey(key)) {
                dbByCanonical.put(key, dbIng);
            }
        }

        List<IngredientDto> merged = new ArrayList<>(externalIngredients.size());

        // Walk external list in order; if DB has a matching canonical → DB wins.
        for (IngredientDto ext : externalIngredients) {
            String key = normalizeKey(ext.canonical());
            if (key.isEmpty()) {
                key = normalizeKey(ext.id());
                if (key.isEmpty()) {
                    key = normalizeKey(ext.original());
                }
            }

            if (!key.isEmpty() && dbByCanonical.containsKey(key)) {
                // Use DB-curated ingredient
                merged.add(dbByCanonical.get(key));
            } else {
                // Use external ingredient as-is
                merged.add(ext);
            }
        }

        return merged;
    }

    private static String normalizeKey(String s) {
        if (s == null) return "";
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
