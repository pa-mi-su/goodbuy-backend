package app.goodbuy.core.products.scoring;

import java.util.List;

/**
 * Result of product-level scoring.
 *
 * safetyScore  : 0–99 numeric score (0 when unrated)
 * ratingLetter : A–F grade, or "NR" when the product is not rated
 * reasons      : high-level explanation for the product score
 *                or why it is unrated.
 */
public record ProductScoreResult(
        int safetyScore,
        String ratingLetter,
        List<String> reasons
) {

    /**
     * Convenience factory for an "unrated" product score.
     *
     * @param reason human-readable explanation of why the product is not rated
     * @return ProductScoreResult with safetyScore=0, ratingLetter="NR"
     */
    public static ProductScoreResult unrated(String reason) {
        return new ProductScoreResult(0, "NR", List.of(reason));
    }

    /**
     * Convenience factory for an "unrated" product score with multiple reasons.
     *
     * @param reasons list of human-readable explanations
     * @return ProductScoreResult with safetyScore=0, ratingLetter="NR"
     */
    public static ProductScoreResult unrated(List<String> reasons) {
        return new ProductScoreResult(0, "NR", List.copyOf(reasons));
    }
}
