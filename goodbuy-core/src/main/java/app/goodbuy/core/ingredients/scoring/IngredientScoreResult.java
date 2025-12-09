package app.goodbuy.core.ingredients.scoring;

import java.util.List;

/**
 * Output of the IngredientScoringEngine.
 *
 * This is what we can:
 * - persist onto the Ingredient row (safetyScore, ratingLetter)
 * - expose via API
 * - show in the UI, including the "reasons" list
 */
public record IngredientScoreResult(
        int safetyScore,        // 0–100
        String ratingLetter,    // "A"–"F"
        List<String> reasons    // human-readable explanation snippets
) {

    public IngredientScoreResult {
        if (ratingLetter == null || ratingLetter.isBlank()) {
            throw new IllegalArgumentException("ratingLetter must not be null/blank");
        }
        if (reasons == null) {
            throw new IllegalArgumentException("reasons must not be null");
        }
    }
}
