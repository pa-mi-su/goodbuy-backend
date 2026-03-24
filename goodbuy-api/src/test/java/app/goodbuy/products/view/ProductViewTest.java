package app.goodbuy.products.view;

import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.ingredients.IngredientReadService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ProductViewTest {

    @Test
    void usesImmediateIngredientReadsWhenDbLookupHasNotCaughtUpYet() {
        IngredientReadService ingredientReadService = mock(IngredientReadService.class);
        when(ingredientReadService.searchRanked("Biotin", false)).thenReturn(Optional.empty());
        when(ingredientReadService.searchRanked("Glycerin", false)).thenReturn(Optional.empty());

        ProductView view = ProductView.of(
                dto(),
                "EAN-DB",
                ingredientReadService,
                true,
                "vitamins",
                List.of(
                        ingredient("biotin", "Biotin", 90, "A"),
                        ingredient("glycerin", "Glycerin", 82, "B")
                )
        );

        assertEquals("A", view.ratingLetter());
        assertTrue(view.safetyScore().intValue() > 0);
        assertEquals(2, view.ingredients().size());
        assertEquals("biotin", view.ingredients().get(0).canonicalKey());
        assertTrue(view.ingredients().get(0).inCatalog());
        assertEquals("A", view.ingredients().get(0).ratingLetter());
    }

    private static ProductDetailDto dto() {
        return new ProductDetailDto(
                "00016500586579",
                "One A Day Prenatal Advanced Multivitamin",
                "One A Day",
                "Vitamins & Supplements",
                null,
                List.of(),
                List.of(
                        new ProductDetailDto.IngredientDto(null, "Biotin", null, Map.of(), null, null),
                        new ProductDetailDto.IngredientDto(null, "Glycerin", null, Map.of(), null, null)
                ),
                Map.of(),
                Map.of(),
                "EAN-DB",
                "vitamins",
                null,
                "NR"
        );
    }

    private static IngredientDTO ingredient(String canonicalKey, String displayName, int safetyScore, String ratingLetter) {
        return new IngredientDTO(
                1L,
                canonicalKey,
                displayName,
                "summary",
                "description",
                "formula ingredient",
                "concerns",
                BigDecimal.valueOf(safetyScore),
                ratingLetter,
                0,
                "ingredient",
                "note",
                true,
                List.of("provisional"),
                List.of(displayName),
                null
        );
    }
}
