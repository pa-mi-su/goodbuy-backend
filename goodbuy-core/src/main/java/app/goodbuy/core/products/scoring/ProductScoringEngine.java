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
        return score(ingredientScores, "unknown");
    }

    public ProductScoreResult score(List<IngredientScoreResult> ingredientScores, String domain) {

        List<String> reasons = new ArrayList<>();

        if (ingredientScores == null || ingredientScores.isEmpty()) {
            reasons.add("No ingredient data available to score this product.");
            return ProductScoreResult.unrated(reasons);
        }

        long unratedCount = ingredientScores.stream()
                .filter(ing -> ing == null || !ing.isRated())
                .count();
        if (unratedCount > 0) {
            reasons.add("Not enough authoritative ingredient evidence to rate " + unratedCount + " ingredient(s) yet.");
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
        reasons.add("Guidance score starts from the average rated ingredient score: " + avgScore + ".");

        boolean sensitiveDomain = isSensitiveDomain(domain);
        boolean cleaningDomain = isCleaningDomain(domain);

        if (countF > 0) {
            int penalty = cleaningDomain ? 8 + (countF - 1) * 4 : 12 + (countF - 1) * 5;
            score -= penalty;
            score = Math.min(score, cleaningDomain ? 58 : (sensitiveDomain ? 56 : 58));
            reasons.add("Contains " + countF + " high-concern ingredient(s) (-" + penalty + ").");
        }

        if (countD > 0) {
            int penalty = cleaningDomain ? 4 + Math.max(0, countD - 1) * 2 : 7 + Math.max(0, countD - 1) * 3;
            score -= penalty;
            score = Math.min(score, cleaningDomain ? 68 : (sensitiveDomain ? 62 : 64));
            reasons.add("Contains " + countD + " moderate-concern ingredient(s) (-" + penalty + ").");
        }

        int countCOrWorse = countC + countD + countF;
        if (countCOrWorse > 0) {
            double fracCOrWorse = countCOrWorse / (double) total;
            if (countC > 0) {
                score = Math.min(score, cleaningDomain ? 74 : 72);
            }
            if (fracCOrWorse >= 0.25) {
                int penalty = cleaningDomain ? 2 : 4;
                score -= penalty;
                reasons.add("A sizable share of ingredients land in the mid-to-high concern range (-" + penalty + ").");
            }
        }

        if (countD + countF >= 2) {
            score = Math.min(score, cleaningDomain ? 62 : (sensitiveDomain ? 52 : 56));
            reasons.add("Multiple concerning ingredients cap the overall guidance score.");
        }

        if (countA + countB == total && avgScore >= 82) {
            int bonus = cleaningDomain ? 2 : 3;
            score += bonus;
            reasons.add("Most ingredients fall in the low-concern range (+" + bonus + ").");
        }

        score = Math.max(0, Math.min(99, score));

        String grade = mapGrade(score);

        return new ProductScoreResult(score, grade, List.copyOf(reasons));
    }

    private boolean isSensitiveDomain(String domain) {
        if (domain == null) {
            return false;
        }
        String normalized = domain.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.contains("food")
                || normalized.contains("supplement")
                || normalized.contains("vitamin")
                || normalized.contains("beauty")
                || normalized.contains("cosmetic")
                || normalized.contains("baby");
    }

    private boolean isCleaningDomain(String domain) {
        if (domain == null) {
            return false;
        }
        return domain.trim().toLowerCase(java.util.Locale.ROOT).contains("cleaning");
    }

    private String mapGrade(int score) {
        if (score >= 88) return "A";
        if (score >= 74) return "B";
        if (score >= 58) return "C";
        if (score >= 42) return "D";
        return "F";
    }
}
