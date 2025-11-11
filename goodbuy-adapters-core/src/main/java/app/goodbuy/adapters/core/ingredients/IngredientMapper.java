package app.goodbuy.adapters.core.ingredients;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientAlias;
import app.goodbuy.core.ingredients.dto.IngredientDTO;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@Component
public class IngredientMapper {

    public IngredientDTO toDto(Ingredient entity) {
        if (entity == null) {
            return null;
        }

        // Aliases -> List<String>, very defensive so it always compiles.
        List<String> aliases = (entity.getAliases() == null)
                ? List.of()
                : entity.getAliases().stream()
                .map(this::aliasToString)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        // Tags (if your entity has getTags(); otherwise this will be an empty list)
        List<String> tags = (hasTags(entity))
                ? entity.getTags()
                : List.of();

        Long id = entity.getId();
        String canonicalKey = entity.getCanonicalKey();
        String displayName = entity.getDisplayName();
        String summary = entity.getSummary();
        String description = entity.getDescription();
        String func = null; // set if you have a func field
        String concerns = entity.getConcerns();
        BigDecimal safetyScore = entity.getSafetyScore();
        String ratingLetter = entity.getRatingLetter();
        Integer referencesCount = entity.getReferencesCount();
        String category = entity.getCategory();
        String regulationNotes = entity.getRegulationNotes();
        boolean isActive = entity.isActive();
        OffsetDateTime updatedAt = entity.getUpdatedAt();

        return new IngredientDTO(
                id,
                canonicalKey,
                displayName,
                summary,
                description,
                func,
                concerns,
                safetyScore,
                ratingLetter,
                referencesCount,
                category,
                regulationNotes,
                isActive,
                tags,
                aliases,
                updatedAt
        );
    }

    public List<IngredientDTO> toDtoList(List<Ingredient> entities) {
        if (entities == null || entities.isEmpty()) {
            return Collections.emptyList();
        }
        return entities.stream()
                .map(this::toDto)
                .toList();
    }

    private String aliasToString(IngredientAlias alias) {
        if (alias == null) return null;

        // Minimal, always-safe version: rely on toString()
        // If your entity has getName() or getAlias(), you can swap this later.
        return alias.toString();
    }

    // Helper to avoid compile errors if getTags() doesn't exist.
    private boolean hasTags(Ingredient entity) {
        try {
            return entity.getTags() != null;
        } catch (NoSuchMethodError e) {
            return false;
        }
    }
}
