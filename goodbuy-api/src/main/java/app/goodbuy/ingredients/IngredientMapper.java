package app.goodbuy.ingredients;

import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.ingredients.model.Ingredient;
import app.goodbuy.ingredients.model.IngredientAlias;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Component
public class IngredientMapper {

    /** Convert a single Ingredient entity → core DTO. */
    public IngredientDTO toDto(Ingredient e) {
        if (e == null) return null;

        List<String> tags = safeList(e.getTags()).stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .sorted(String.CASE_INSENSITIVE_ORDER)
                .collect(Collectors.toList());

        List<String> aliases = safeList(e.getAliases()).stream()
                .filter(Objects::nonNull)
                .map(IngredientAlias::getAlias)
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .sorted(Comparator.comparing(s -> s, String.CASE_INSENSITIVE_ORDER))
                .collect(Collectors.toList());

        // IMPORTANT: Argument order must match IngredientDTO definition in core.
        return new IngredientDTO(
                e.getId(),
                e.getCanonicalKey(),
                e.getDisplayName(),
                e.getSummary(),
                e.getDescription(),
                e.getFuncUse(),
                e.getConcerns(),
                e.getSafetyScore(),
                e.getRatingLetter(),
                e.getReferencesCount(),
                e.getCategory(),
                e.getRegulationNotes(),
                e.isActive(),
                tags,
                aliases,
                e.getUpdatedAt()
        );
    }

    /** Convert a collection of Ingredient entities → DTO list. */
    public List<IngredientDTO> toDtoList(Collection<Ingredient> entities) {
        if (entities == null || entities.isEmpty()) return List.of();
        return entities.stream()
                .filter(Objects::nonNull)
                .map(this::toDto)
                .toList();
    }

    private static <T> List<T> safeList(List<T> list) {
        return list == null ? List.of() : list;
    }
}
