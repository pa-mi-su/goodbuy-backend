package app.goodbuy.ingredients;

import app.goodbuy.adapters.core.ingredients.IngredientMapper;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.service.IngredientCreationService;
import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentQueuePort;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class IngredientOnDemandResearchService {

    private final IngredientRepository repo;
    private final IngredientMapper mapper;
    private final IngredientCreationService ingredientCreationService;
    private final IngredientEnrichmentQueuePort enrichmentQueue;

    public IngredientOnDemandResearchService(
            IngredientRepository repo,
            IngredientMapper mapper,
            IngredientCreationService ingredientCreationService,
            IngredientEnrichmentQueuePort enrichmentQueue
    ) {
        this.repo = repo;
        this.mapper = mapper;
        this.ingredientCreationService = ingredientCreationService;
        this.enrichmentQueue = enrichmentQueue;
    }

    public IngredientDTO getOrStartResearch(String rawQuery) {
        String query = normalizeNeedle(rawQuery);
        Ingredient ingredient = findBestEntity(query);
        if (ingredient == null) {
            ingredient = createSkeletonIngredient(rawQuery, query);
        }

        if (shouldResearch(ingredient)) {
            enrichmentQueue.enqueue(ingredient.getId(), "ingredient_detail_lookup");
        }

        return mapper.toDto(ingredient);
    }

    private Ingredient findBestEntity(String query) {
        if (query.isEmpty()) {
            return null;
        }

        Ingredient byCanonical = repo.findByCanonicalKeyIgnoreCase(query).orElse(null);
        if (byCanonical != null) {
            return byCanonical;
        }

        List<Ingredient> aliasHits = repo.findByAliasExact(query);
        if (!aliasHits.isEmpty()) {
            return aliasHits.stream()
                    .sorted(Comparator.comparingLong(Ingredient::getId))
                    .findFirst()
                    .orElse(null);
        }

        List<Ingredient> loose = repo.searchLoose(query);
        if (loose.isEmpty()) {
            return null;
        }

        return loose.stream()
                .sorted(Comparator
                        .comparingInt((Ingredient i) -> looseRank(i, query))
                        .thenComparingLong(Ingredient::getId))
                .findFirst()
                .orElse(null);
    }

    private Ingredient createSkeletonIngredient(String rawQuery, String canonicalKey) {
        Ingredient created = new Ingredient();
        created.setCanonicalKey(canonicalKey);
        created.setDisplayName((rawQuery == null || rawQuery.isBlank()) ? canonicalKey : rawQuery.trim());
        created.setActive(true);
        try {
            Long ingredientId = ingredientCreationService.createIngredient(created);
            return repo.findById(ingredientId).orElseThrow();
        } catch (DataIntegrityViolationException ex) {
            return repo.findByCanonicalKeyIgnoreCase(canonicalKey).orElseThrow(() -> ex);
        }
    }

    private boolean shouldResearch(Ingredient ingredient) {
        return ingredient != null && ingredient.getId() != null && (
                ingredient.getSafetyScore() == null
                        || isBlank(ingredient.getSummary())
                        || isBlank(ingredient.getDescription())
                        || isBlank(ingredient.getFuncUse())
                        || isBlank(ingredient.getConcerns())
                        || ingredient.getReferencesCount() == null
                        || ingredient.getReferencesCount() <= 0
        );
    }

    private int looseRank(Ingredient ingredient, String query) {
        String canonical = normalizeNeedle(ingredient.getCanonicalKey());
        String display = normalizeNeedle(ingredient.getDisplayName());
        if (canonical.equals(query)) return 0;
        if (display.equals(query)) return 1;
        if (!canonical.isEmpty() && canonical.contains(query)) return 10;
        if (!display.isEmpty() && display.contains(query)) return 11;
        return 99;
    }

    private String normalizeNeedle(String raw) {
        if (raw == null) return "";

        return raw
                .toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")
                .replace('’', '\'')
                .replace('–', '-')
                .replace('—', '-');
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
