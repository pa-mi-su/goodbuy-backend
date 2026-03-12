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
        String ratingLetter,    // "A"–"F" or "NR"
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

    public static IngredientScoreResult unrated(String reason) {
        return new IngredientScoreResult(0, "NR", List.of(reason));
    }

    public boolean isRated() {
        return !"NR".equalsIgnoreCase(ratingLetter);
    }
}
