package app.goodbuy.core.products.ingredients;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IngredientTextParserTest {

    @Test
    void parsesBasicIngredientLabelText() {
        List<String> parsed = IngredientTextParser.parse(
                "Ingredients: Calcium Carbonate, Microcrystalline Cellulose, Gelatin"
        );

        assertEquals(
                List.of("Calcium Carbonate", "Microcrystalline Cellulose", "Gelatin"),
                parsed
        );
    }

    @Test
    void expandsLessThanSectionIntoRealIngredients() {
        List<String> parsed = IngredientTextParser.parse(
                "Calcium Carbonate, Less than 2% of: silicon dioxide, titanium dioxide, red 40 lake"
        );

        assertEquals(
                List.of("Calcium Carbonate", "silicon dioxide", "titanium dioxide", "red 40 lake"),
                parsed
        );
    }

    @Test
    void preservesCommasInsideParentheses() {
        List<String> parsed = IngredientTextParser.parse(
                "Fragrance (parfum, limonene), Water; Glycerin"
        );

        assertEquals(
                List.of("Fragrance (parfum, limonene)", "Water", "Glycerin"),
                parsed
        );
    }
}
