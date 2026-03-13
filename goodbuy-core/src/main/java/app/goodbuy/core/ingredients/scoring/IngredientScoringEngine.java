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
        return score(null, signals);
    }

    /**
     * Conservative scoring entrypoint that considers both structured hazard signals
     * and curated ingredient rules.
     */
    public IngredientScoreResult score(String canonicalKey, IngredientSignals signals) {
        IngredientSignals effectiveSignals = signals == null ? IngredientSignals.empty() : signals;
        List<CuratedIngredientRule> curatedRules = CuratedIngredientRules.findAll(canonicalKey);
        boolean hasStructuredSignals = effectiveSignals.hasAnySignal();

        if (!hasStructuredSignals && curatedRules.isEmpty()) {
            return IngredientScoreResult.unrated("Insufficient authoritative evidence to rate this ingredient yet.");
        }

        int score = curatedRules.stream()
                .map(CuratedIngredientRule::baseScoreOverride)
                .filter(java.util.Objects::nonNull)
                .max(Integer::compareTo)
                .orElse(85);
        List<String> reasons = new ArrayList<>();
        curatedRules.stream()
                .flatMap(rule -> rule.reasons().stream())
                .distinct()
                .forEach(reasons::add);

        boolean ignorePubchem = curatedRules.stream().anyMatch(CuratedIngredientRule::ignorePubchem);
        Integer scoreCap = curatedRules.stream()
                .map(CuratedIngredientRule::scoreCap)
                .filter(java.util.Objects::nonNull)
                .min(Integer::compareTo)
                .orElse(null);

        boolean hasNegativeSignal = false;

        // ─────────────────────────────────────
        // IARC carcinogenicity groups
        // ─────────────────────────────────────
        if (effectiveSignals.iarcGroup().isPresent()) {
            int g = effectiveSignals.iarcGroup().get();

            if (g == 1) {
                score -= 55;
                scoreCap = minCap(scoreCap, 20);
                hasNegativeSignal = true;
                reasons.add("IARC Group 1 carcinogen (-55)");
            } else if (g == 2) {
                score -= 35;
                scoreCap = minCap(scoreCap, 40);
                hasNegativeSignal = true;
                reasons.add("IARC Group 2A/2B carcinogen (-35)");
            } else {
                score -= 20;
                scoreCap = minCap(scoreCap, 55);
                hasNegativeSignal = true;
                reasons.add("IARC lower-confidence carcinogenicity signal (-20)");
            }
        }

        // ─────────────────────────────────────
        // California Proposition 65
        // ─────────────────────────────────────
        if (effectiveSignals.prop65Listed()) {
            score -= 25;
            scoreCap = minCap(scoreCap, 45);
            hasNegativeSignal = true;
            reasons.add("California Prop 65 listed chemical (-25)");
        }

        // ─────────────────────────────────────
        // EWG numeric score (1–10)
        // ─────────────────────────────────────
        if (effectiveSignals.ewgScore().isPresent()) {
            int ewg = effectiveSignals.ewgScore().get();

            if (ewg >= 8) {
                score -= 25;
                scoreCap = minCap(scoreCap, 45);
                hasNegativeSignal = true;
                reasons.add("EWG high concern rating (" + ewg + "/10) (-25)");
            } else if (ewg >= 5) {
                score -= 15;
                scoreCap = minCap(scoreCap, 65);
                hasNegativeSignal = true;
                reasons.add("EWG moderate concern rating (" + ewg + "/10) (-15)");
            } else if (ewg <= 2 && !hasNegativeSignal) {
                score = Math.max(score, 92);
                reasons.add("EWG low concern rating (" + ewg + "/10)");
            }
        }

        // ─────────────────────────────────────
        // EU regulatory flags
        // ─────────────────────────────────────
        if (effectiveSignals.euProhibited()) {
            score -= 60;
            scoreCap = minCap(scoreCap, 10);
            hasNegativeSignal = true;
            reasons.add("EU-prohibited ingredient (-60)");
        } else if (effectiveSignals.euRestricted()) {
            score -= 20;
            scoreCap = minCap(scoreCap, 60);
            hasNegativeSignal = true;
            reasons.add("EU restricted-use ingredient (-20)");
        }

        // ─────────────────────────────────────
        // PubChem hazard flags
        // ─────────────────────────────────────
        if (!ignorePubchem) {
            if (effectiveSignals.pubchemMutagen()) {
                score -= 20;
                scoreCap = minCap(scoreCap, 55);
                hasNegativeSignal = true;
                reasons.add("Mutagenicity / genotoxicity hazard identified (-20)");
            }

            if (effectiveSignals.pubchemReproductiveToxin()) {
                score -= 20;
                scoreCap = minCap(scoreCap, 55);
                hasNegativeSignal = true;
                reasons.add("Reproductive toxicity hazard (-20)");
            }
        }

        // ─────────────────────────────────────
        // EPA chronic toxicity
        // ─────────────────────────────────────
        if (effectiveSignals.epaChronicToxicity()) {
            score -= 15;
            scoreCap = minCap(scoreCap, 65);
            hasNegativeSignal = true;
            reasons.add("EPA chronic toxicity signal (-15)");
        }

        // ─────────────────────────────────────
        // Irritation / sensitization
        // ─────────────────────────────────────
        if (effectiveSignals.skinIrritant()) {
            score -= 8;
            scoreCap = minCap(scoreCap, 75);
            hasNegativeSignal = true;
            reasons.add("Skin/eye irritant or sensitizer (-8)");
        }

        if (scoreCap != null) {
            score = Math.min(score, scoreCap);
        }

        score = Math.max(0, Math.min(99, score));

        String grade = mapGrade(score);

        return new IngredientScoreResult(score, grade, List.copyOf(reasons));
    }

    private Integer minCap(Integer currentCap, int nextCap) {
        return currentCap == null ? nextCap : Math.min(currentCap, nextCap);
    }

    private String mapGrade(int score) {
        if (score >= 90) return "A";
        if (score >= 80) return "B";
        if (score >= 70) return "C";
        if (score >= 55) return "D";
        return "F";
    }
}
