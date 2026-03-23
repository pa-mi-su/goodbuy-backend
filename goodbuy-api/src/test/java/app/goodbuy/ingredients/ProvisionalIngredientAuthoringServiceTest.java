package app.goodbuy.ingredients;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProvisionalIngredientAuthoringServiceTest {

    private final ProvisionalIngredientAuthoringService service = new ProvisionalIngredientAuthoringService();

    @Test
    void assignsUsableFallbackContentAndScoreToGenericIngredient() {
        Ingredient ingredient = new Ingredient();
        ingredient.setCanonicalKey("unknown blend");
        ingredient.setDisplayName("Unknown Blend");

        service.applyProvisionalProfile(ingredient, "Unknown Blend", "unknown blend");

        assertEquals("C", ingredient.getRatingLetter());
        assertEquals(70, ingredient.getSafetyScore().intValue());
        assertNotNull(ingredient.getSummary());
        assertNotNull(ingredient.getDescription());
        assertNotNull(ingredient.getFuncUse());
        assertNotNull(ingredient.getConcerns());
        assertFalse(ingredient.getAliases().isEmpty());
    }

    @Test
    void assignsLowConcernFallbackToVitaminLikeIngredient() {
        Ingredient ingredient = new Ingredient();
        ingredient.setCanonicalKey("biotin");
        ingredient.setDisplayName("Biotin");

        service.applyProvisionalProfile(ingredient, "Biotin", "biotin");

        assertEquals("A", ingredient.getRatingLetter());
        assertEquals(90, ingredient.getSafetyScore().intValue());
        assertEquals("vitamin", ingredient.getCategory());
    }
}
