package app.goodbuy.core.ingredients.scoring;

import java.util.Optional;

/**
 * Normalized hazard/regulatory “signals” for an ingredient.
 *
 * Two optional numeric signals:
 *  - iarcGroup:    Optional IARC carcinogenicity group (1, 2, 3, ...)
 *  - ewgScore:     Optional EWG numeric score (1–10, where higher = more concern)
 *
 * The rest are boolean flags for specific hazard systems.
 */
public record IngredientSignals(
        Optional<Integer> iarcGroup,
        Optional<Integer> ewgScore,
        boolean prop65Listed,
        boolean euProhibited,
        boolean euRestricted,
        boolean pubchemMutagen,
        boolean pubchemReproductiveToxin,
        boolean epaChronicToxicity,
        boolean skinIrritant
) {

    /**
     * Neutral / “no hazard data” signal set.
     *
     * The scoring engine will treat this as:
     *   - no carcinogenicity / EWG info
     *   - no Prop 65 / EU / PubChem / EPA flags
     */
    public static IngredientSignals empty() {
        return new IngredientSignals(
                Optional.empty(),  // iarcGroup
                Optional.empty(),  // ewgScore
                false,             // prop65Listed
                false,             // euProhibited
                false,             // euRestricted
                false,             // pubchemMutagen
                false,             // pubchemReproductiveToxin
                false,             // epaChronicToxicity
                false              // skinIrritant
        );
    }

    /**
     * @return true if we have ANY non-neutral signal (numeric or boolean).
     *
     * Used by the adapter layer so we don't assign a default 95/A to
     * completely unknown ingredients.
     */
    public boolean hasAnySignal() {
        return iarcGroup.isPresent()
                || ewgScore.isPresent()
                || prop65Listed
                || euProhibited
                || euRestricted
                || pubchemMutagen
                || pubchemReproductiveToxin
                || epaChronicToxicity
                || skinIrritant;
    }
}
