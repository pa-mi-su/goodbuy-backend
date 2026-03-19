package app.goodbuy.core.ingredients.port;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Port for ingredient enrichment (PubChem now, more sources later).
 *
 * Key design goals:
 *  - DOES NOT depend on JPA entities
 *  - Returns a stable, minimal enrichment payload we can persist
 *  - Supports aliases + citations so we can improve matching and show sources
 *
 * IMPORTANT:
 *  - This is NOT called inside request/scan path.
 *  - Enrichment should run in a background job or admin trigger.
 */
public interface IngredientEnrichmentPort {

    /**
     * Attempt to enrich an ingredient.
     *
     * @param req request details; canonicalKey should already be normalized
     * @return Optional.empty() if no match / no data found; otherwise enrichment payload
     */
    Optional<IngredientEnrichment> enrich(EnrichmentRequest req);

    record EnrichmentRequest(
            Long ingredientId,                 // DB id (for logging)
            String canonicalKey,               // normalized key (lowercase, collapsed spaces)
            String displayName,                // current display name (best user-facing)
            Map<String, String> externalIds,   // optional (e.g., from EAN-DB metadata)
            boolean allowPaidSources           // future-proof; ignored by free sources
    ) {}

    /**
     * What we learned from the enrichment source(s).
     *
     * Rules for the first implementation:
     *  - If a field is null => "no update"
     *  - If aliases is empty/null => don't add aliases
     *  - citations are optional but strongly recommended (you already have tables)
     */
    record IngredientEnrichment(
            // Basic content
            String summary,
            String description,
            String category,
            String funcUse,             // maps to ingredients.func_use
            String concerns,
            String regulationNotes,

            // Optional “signals” we can safely set from PubChem or other sources
            Signals signals,

            // Aliases/synonyms to improve matching hits
            List<String> aliases,

            // Citations backing the enrichment (source registry + URLs)
            List<Citation> citations,

            // Debug metadata (optional)
            String provider,            // "PubChem"
            String providerRef,         // e.g., CID or other stable id
            OffsetDateTime enrichedAt
    ) {}

    /**
     * Conservative boolean flags only.
     * If we cannot confidently derive, leave null (so we don't overwrite existing).
     */
    record Signals(
            Boolean pubchemMutagen,
            Boolean pubchemReproductiveToxin,
            Boolean skinIrritant
    ) {}

    record Citation(
            String sourceName,          // e.g., "PubChem"
            String url,                 // canonical URL
            String title                // short title if available
    ) {}
}
