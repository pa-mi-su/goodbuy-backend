package app.goodbuy.ingredients;

import app.goodbuy.adapters.core.ingredients.IngredientMapper;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.service.IngredientCreationService;
import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentQueuePort;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class IngredientOnDemandResearchServiceTest {

    private final IngredientRepository repo = mock(IngredientRepository.class);
    private final IngredientMapper mapper = new IngredientMapper();
    private final IngredientCreationService creationService = mock(IngredientCreationService.class);
    private final IngredientEnrichmentQueuePort queue = mock(IngredientEnrichmentQueuePort.class);
    private final ProvisionalIngredientAuthoringService provisionalIngredientAuthoringService =
            new ProvisionalIngredientAuthoringService();

    private final IngredientOnDemandResearchService service =
            new IngredientOnDemandResearchService(repo, mapper, creationService, queue, provisionalIngredientAuthoringService);

    @Test
    void existingUnscoredIngredientQueuesResearch() {
        Ingredient ingredient = ingredient(7L, "microcrystalline cellulose", "Microcrystalline Cellulose");
        when(repo.findByCanonicalKeyIgnoreCase("microcrystalline cellulose")).thenReturn(Optional.of(ingredient));
        when(repo.saveAndFlush(any(Ingredient.class))).thenAnswer(invocation -> invocation.getArgument(0));

        IngredientDTO dto = service.getOrStartResearch("Microcrystalline Cellulose");

        assertEquals(7L, dto.id());
        verify(queue).enqueue(7L, "ingredient_detail_lookup");
    }

    @Test
    void missingIngredientCreatesSkeletonAndQueuesResearch() {
        Ingredient created = ingredient(11L, "unknown blend", "Unknown Blend");
        provisionalIngredientAuthoringService.applyProvisionalProfile(created, "Unknown Blend", "unknown blend");
        when(repo.findByCanonicalKeyIgnoreCase("unknown blend")).thenReturn(Optional.empty());
        when(repo.findByAliasExact("unknown blend")).thenReturn(List.of());
        when(repo.searchLoose("unknown blend")).thenReturn(List.of());
        when(creationService.createIngredient(any(Ingredient.class))).thenReturn(11L);
        when(repo.findById(11L)).thenReturn(Optional.of(created));

        IngredientDTO dto = service.getOrStartResearch("Unknown Blend");

        assertEquals(11L, dto.id());
        assertEquals("C", dto.ratingLetter());
        verify(creationService).createIngredient(any(Ingredient.class));
        verify(queue).enqueue(11L, "ingredient_detail_lookup");
    }

    private Ingredient ingredient(long id, String canonicalKey, String displayName) {
        Ingredient ingredient = new Ingredient();
        ingredient.setCanonicalKey(canonicalKey);
        ingredient.setDisplayName(displayName);
        try {
            var idField = Ingredient.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(ingredient, id);
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
        return ingredient;
    }
}
