package app.goodbuy.ingredients;

import app.goodbuy.adapters.core.citations.service.IngredientCitationWriter;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.service.IngredientSignalsWriter;
import app.goodbuy.core.ingredients.port.IngredientAutoEnricherPort;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentResult;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AsyncIngredientResearchWorkerTest {

    @Test
    void researchUsesAlternateQueriesAndPersistsBestResult() {
        IngredientRepository repo = mock(IngredientRepository.class);
        IngredientAutoEnricherPort enricher = mock(IngredientAutoEnricherPort.class);
        IngredientCitationWriter citationWriter = mock(IngredientCitationWriter.class);
        IngredientSignalsWriter signalsWriter = mock(IngredientSignalsWriter.class);

        Ingredient ingredient = new Ingredient();
        ingredient.setCanonicalKey("dl-alpha-tocopherol acetate");
        ingredient.setDisplayName("dl-alpha-tocopherol acetate (vitamin e)");
        setId(ingredient, 5L);

        when(repo.findByIdForUpdate(5L)).thenReturn(Optional.of(ingredient));
        when(enricher.enrich(any())).thenReturn(
                new IngredientEnrichmentResult(false, null, null, null, null, null, null, null, 0, List.of(), List.of(), List.of(), null, null, null, null, null, null, null, null, null, null, null, null),
                new IngredientEnrichmentResult(true, "Vitamin E Acetate", "summary", "description", "function", "concerns", "vitamins", "notes", 1, List.of("tocopherol acetate"), List.of(), List.of("https://example.com"), "OPENAI", "title", "ok", null, false, null, false, false, false, false, false, false)
        );
        when(signalsWriter.upsertSignalsAndScore(eq(5L), any())).thenAnswer(invocation -> {
            ingredient.setSafetyScore(new BigDecimal("85"));
            ingredient.setRatingLetter("B");
            return true;
        });

        AsyncIngredientResearchWorker worker = new AsyncIngredientResearchWorker(
                repo,
                Optional.of(enricher),
                citationWriter,
                signalsWriter
        );

        worker.researchAsync(5L, "test", null);

        verify(enricher, atLeast(2)).enrich(any());
        verify(signalsWriter).upsertSignalsAndScore(eq(5L), any());
        assertTrue(ingredient.getAliases().stream().anyMatch(a -> "tocopherol acetate".equals(a.getAlias())));
    }

    private static void setId(Ingredient ingredient, long id) {
        try {
            var idField = Ingredient.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(ingredient, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }
}
