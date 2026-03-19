package app.goodbuy.core.ingredients.port;

/**
 * Enqueue an enrichment job for an ingredient.
 *
 * This is intentionally simple:
 * - ingestion stays fast
 * - enrichment happens asynchronously with retries
 *
 * Implementations should be idempotent (enqueue-once).
 */
public interface IngredientEnrichmentQueuePort {

    /**
     * Enqueue enrichment for the given ingredient id.
     *
     * @param ingredientId DB id of the ingredient
     * @param reason short reason for logging/debugging
     */
    void enqueue(long ingredientId, String reason);
}
