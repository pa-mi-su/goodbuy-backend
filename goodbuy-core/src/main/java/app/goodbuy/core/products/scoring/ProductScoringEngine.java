package app.goodbuy.core.products.scoring;

import app.goodbuy.core.ingredients.scoring.IngredientScoreResult;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure product-level scoring engine.
 *
 * Takes ingredient-level scores and produces:
 * - product safetyScore (0–99)
 * - product ratingLetter (A–F or "NR")
 * - high-level reasons (for UI explainability)
 *
 * NO Spring, NO persistence – just math.
 */
public class ProductScoringEngine {

    /**
     * Score a product given its ingredient scores.
     *
     * @param ingredientScores list of ingredient scores for this product
     * @return product score + letter grade + reasons
     */
    public ProductScoreResult score(List<IngredientScoreResult> ingredientScores) {

        List<String> reasons = new ArrayList<>();

        // ─────────────────────────────────────
        // Edge case: no ingredients -> UNRATED
        // ─────────────────────────────────────
        if (ingredientScores == null || ingredientScores.isEmpty()) {
            reasons.add("No ingredient data available to score this product.");
            return ProductScoreResult.unrated(reasons);
        }

        int total = ingredientScores.size();

        int sumScores = 0;
        int countA = 0;
        int countB = 0;
        int countC = 0;
        int countD = 0;
        int countF = 0;

        for (IngredientScoreResult ing : ingredientScores) {
            int s = ing.safetyScore();
            sumScores += s;

            String g = ing.ratingLetter();
            if ("A".equalsIgnoreCase(g)) countA++;
            else if ("B".equalsIgnoreCase(g)) countB++;
            else if ("C".equalsIgnoreCase(g)) countC++;
            else if ("D".equalsIgnoreCase(g)) countD++;
            else if ("F".equalsIgnoreCase(g)) countF++;
        }

        int avgScore = Math.round(sumScores / (float) total);
        int score = avgScore;
        reasons.add("Average ingredient score: " + avgScore + ".");

        // ─────────────────────────────────────
        // Penalty: any F ingredients
        // ─────────────────────────────────────
        if (countF > 0) {
            int penalty = 20;
            score -= penalty;
            reasons.add("Contains " + countF + " high-concern (F) ingredient(s) (-" + penalty + ").");
        }

        // ─────────────────────────────────────
        // Penalty: any D ingredients
        // ─────────────────────────────────────
        if (countD > 0) {
            int penalty = 10;
            score -= penalty;
            reasons.add("Contains " + countD + " concerning (D) ingredient(s) (-" + penalty + ").");
        }

        // ─────────────────────────────────────
        // Penalty: many ingredients C or worse
        // ─────────────────────────────────────
        int countCOrWorse = countC + countD + countF;
        if (countCOrWorse > 0) {
            double fracCOrWorse = countCOrWorse / (double) total;
            if (fracCOrWorse >= 0.5) {
                int penalty = 5;
                score -= penalty;
                reasons.add("More than half of ingredients are C or worse (-" + penalty + ").");
            }
        }

        // ─────────────────────────────────────
        // Small boost: all ingredients A or B
        // ─────────────────────────────────────
        if (countC == 0 && countD == 0 && countF == 0) {
            int boost = 3;
            score += boost;
            reasons.add("All ingredients are A or B only (+" + boost + ").");
        }

        // ─────────────────────────────────────
        // Clamp and map grade
        // ─────────────────────────────────────
        score = Math.max(0, Math.min(99, score));

        String grade = mapGrade(score);

        return new ProductScoreResult(score, grade, List.copyOf(reasons));
    }

    private String mapGrade(int score) {
        if (score >= 90) return "A";  // Very safe
        if (score >= 80) return "B";  // Generally safe
        if (score >= 70) return "C";  // Mixed / moderate concern
        if (score >= 55) return "D";  // Concerning
        return "F";                   // High concern
    }
}
