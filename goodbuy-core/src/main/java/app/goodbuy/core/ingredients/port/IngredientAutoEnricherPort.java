package app.goodbuy.core.ingredients.port;

/**
 * Synchronous ingredient enrichment used during product scan ingestion.
 *
 * Contract:
 * - Called BEFORE persisting a newly-created ingredient skeleton.
 * - Must be bounded and safe: failures must not break ingestion.
 */
public interface IngredientAutoEnricherPort {

    IngredientEnrichmentResult enrich(IngredientEnrichmentRequest request);

}
