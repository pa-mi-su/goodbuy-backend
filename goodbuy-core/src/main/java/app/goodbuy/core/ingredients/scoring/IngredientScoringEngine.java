package app.goodbuy.core.ingredients.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * Pure scoring engine.
 *
 * Takes normalized IngredientSignals and produces:
 * - safetyScore (0–100 logical range, but clamped to 0–99 for DB NUMERIC(4,2))
 * - ratingLetter (A–F)
 * - reasons (for explainability in UI)
 *
 * This class intentionally has NO Spring or persistence dependencies
 * so it can be reused from adapters, batch jobs, and API code.
 */
public class IngredientScoringEngine {

    /**
     * Main scoring entrypoint.
     */
    public IngredientScoreResult score(IngredientSignals signals) {

        // Baseline:
        //  - A "very safe / neutral" ingredient should land at ~95 / A.
        //  - Hazards subtract from this baseline.
        int score = 95;
        List<String> reasons = new ArrayList<>();

        // ─────────────────────────────────────
        // IARC carcinogenicity groups
        // ─────────────────────────────────────
        if (signals.iarcGroup() != null && signals.iarcGroup().isPresent()) {
            int g = signals.iarcGroup().get();

            // We normalize:
            // 1  -> Group 1 carcinogen (highest concern)
            // 2  -> Group 2A / 2B bucket
            // 3+ -> lower (but still some) concern
            if (g == 1) {
                score -= 40;
                reasons.add("IARC Group 1 carcinogen (-40)");
            } else if (g == 2) {
                score -= 30;
                reasons.add("IARC Group 2A/2B carcinogen (-30)");
            } else {
                score -= 20;
                reasons.add("IARC lower-confidence carcinogenicity signal (-20)");
            }
        }

        // ─────────────────────────────────────
        // California Proposition 65
        // ─────────────────────────────────────
        if (signals.prop65Listed()) {
            score -= 20;
            reasons.add("California Prop 65 listed chemical (-20)");
        }

        // ─────────────────────────────────────
        // EWG numeric score (1–10)
        // ─────────────────────────────────────
        if (signals.ewgScore() != null && signals.ewgScore().isPresent()) {
            int ewg = signals.ewgScore().get();

            if (ewg >= 8) {
                score -= 20;
                reasons.add("EWG high concern rating (" + ewg + "/10) (-20)");
            } else if (ewg >= 5) {
                score -= 10;
                reasons.add("EWG moderate concern rating (" + ewg + "/10) (-10)");
            } else if (ewg >= 1) {
                // No numerical bonus, but we record the low concern signal
                reasons.add("EWG low concern rating (" + ewg + "/10)");
            }
        }

        // ─────────────────────────────────────
        // EU regulatory flags
        // ─────────────────────────────────────
        if (signals.euProhibited()) {
            score -= 50;
            reasons.add("EU-prohibited ingredient (-50)");
        } else if (signals.euRestricted()) {
            score -= 15;
            reasons.add("EU restricted-use ingredient (-15)");
        }

        // ─────────────────────────────────────
        // PubChem hazard flags
        // ─────────────────────────────────────
        if (signals.pubchemMutagen()) {
            score -= 15;
            reasons.add("Mutagenicity / genotoxicity hazard identified (-15)");
        }

        if (signals.pubchemReproductiveToxin()) {
            score -= 15;
            reasons.add("Reproductive toxicity hazard (-15)");
        }

        // ─────────────────────────────────────
        // EPA chronic toxicity
        // ─────────────────────────────────────
        if (signals.epaChronicToxicity()) {
            score -= 10;
            reasons.add("EPA chronic toxicity signal (-10)");
        }

        // ─────────────────────────────────────
        // Irritation / sensitization
        // ─────────────────────────────────────
        if (signals.skinIrritant()) {
            score -= 5;
            reasons.add("Skin/eye irritant or sensitizer (-5)");
        }

        // ─────────────────────────────────────
        // Clamp and map to letter grade
        // DB column is NUMERIC(4,2) => max 99.99,
        // so we clamp to max 99 to avoid overflow.
        // ─────────────────────────────────────
        score = Math.max(0, Math.min(99, score));

        String grade = mapGrade(score);

        return new IngredientScoreResult(score, grade, List.copyOf(reasons));
    }

    private String mapGrade(int score) {
        if (score >= 90) return "A";  // Very safe
        if (score >= 80) return "B";  // Generally safe
        if (score >= 70) return "C";  // Mixed / moderate concern
        if (score >= 55) return "D";  // Concerning
        return "F";                   // High concern
    }
}
