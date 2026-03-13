package app.goodbuy.core.ingredients.port;

import java.util.Map;

/**
 * Input for synchronous enrichment.
 */
public record IngredientEnrichmentRequest(
        String canonicalKey,
        String displayName,
        Map<String, String> externalIds,
        String sourceProvider,  // e.g. "EAN-DB"
        String productEan       // for traceability/logging
) { }
