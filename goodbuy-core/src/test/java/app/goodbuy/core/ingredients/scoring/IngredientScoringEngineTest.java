package app.goodbuy.core.ingredients.scoring;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngredientScoringEngineTest {

    private final IngredientScoringEngine engine = new IngredientScoringEngine();

    @Test
    void talcIsHighConcernEvenWithoutStructuredSignals() {
        IngredientScoreResult result = engine.score("talc", IngredientSignals.empty());

        assertEquals("F", result.ratingLetter());
        assertTrue(result.safetyScore() <= 35);
    }

    @Test
    void unknownIngredientWithoutEvidenceIsUnrated() {
        IngredientScoreResult result = engine.score("made up ingredient", IngredientSignals.empty());

        assertEquals("NR", result.ratingLetter());
        assertEquals(0, result.safetyScore());
    }

    @Test
    void ascorbicAcidDoesNotGetPunishedByBarePubchemFlags() {
        IngredientScoreResult result = engine.score(
                "ascorbic acid",
                new IngredientSignals(
                        Optional.empty(),
                        Optional.empty(),
                        false,
                        false,
                        false,
                        true,
                        true,
                        false,
                        false
                )
        );

        assertEquals("A", result.ratingLetter());
        assertTrue(result.safetyScore() >= 90);
    }

    @Test
    void fragranceIsCappedWithoutNeedingStructuredSignals() {
        IngredientScoreResult result = engine.score("fragrance", IngredientSignals.empty());

        assertEquals("D", result.ratingLetter());
        assertTrue(result.safetyScore() <= 58);
    }
}
