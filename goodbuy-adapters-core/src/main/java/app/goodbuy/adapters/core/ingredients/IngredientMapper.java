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
import java.util.stream.Collectors;

@Component
public class IngredientMapper {

    public IngredientDTO toDto(Ingredient entity) {
        if (entity == null) {
            return null;
        }

        // Aliases -> List<String>
        List<String> aliases = (entity.getAliases() == null)
                ? List.of()
                : entity.getAliases().stream()
                .map(this::aliasToString)          // use actual alias string
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        // Tags (already wired in your entity)
        List<String> tags = (entity.getTags() == null)
                ? List.of()
                : entity.getTags().stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted()
                .collect(Collectors.toList());

        Long id = entity.getId();
        String canonicalKey = entity.getCanonicalKey();
        String displayName = entity.getDisplayName();
        String summary = entity.getSummary();
        String description = entity.getDescription();

        // Keeping func null for now so we don't guess your field names.
        String func = null;

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
        // Use the actual alias field from your JPA entity
        return alias.getAlias();
    }
}
