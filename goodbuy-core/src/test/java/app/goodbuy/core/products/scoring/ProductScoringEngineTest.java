package app.goodbuy.core.products.scoring;

import app.goodbuy.core.ingredients.scoring.IngredientScoreResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductScoringEngineTest {

    private final ProductScoringEngine engine = new ProductScoringEngine();

    @Test
    void productWithUnratedIngredientIsUnrated() {
        ProductScoreResult result = engine.score(List.of(
                new IngredientScoreResult(92, "A", List.of()),
                IngredientScoreResult.unrated("unknown")
        ), "supplement");

        assertEquals("NR", result.ratingLetter());
    }

    @Test
    void productWithOneFIngredientGetsHardCap() {
        ProductScoreResult result = engine.score(List.of(
                new IngredientScoreResult(92, "A", List.of()),
                new IngredientScoreResult(30, "F", List.of())
        ), "cosmetic");

        assertEquals("F", result.ratingLetter());
        assertTrue(result.safetyScore() <= 25);
    }

    @Test
    void productWithAllABIngredientsStaysRated() {
        ProductScoreResult result = engine.score(List.of(
                new IngredientScoreResult(92, "A", List.of()),
                new IngredientScoreResult(84, "B", List.of()),
                new IngredientScoreResult(88, "B", List.of())
        ), "supplement");

        assertEquals("B", result.ratingLetter());
        assertTrue(result.safetyScore() >= 80);
    }
}
