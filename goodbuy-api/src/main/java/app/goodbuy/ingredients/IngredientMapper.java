package app.goodbuy.ingredients;

import app.goodbuy.ingredients.model.Ingredient;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

@Component
public class IngredientMapper {

    /** Convert a single Ingredient entity → DTO. */
    public app.goodbuy.ingredients.model.IngredientDTO toDto(Ingredient entity) {
        return app.goodbuy.ingredients.model.IngredientDTO.of(entity);
    }

    /** Convert a collection of Ingredient entities → DTO list. */
    public List<app.goodbuy.ingredients.model.IngredientDTO> toDtoList(Collection<Ingredient> entities) {
        if (entities == null || entities.isEmpty()) return List.of();
        return entities.stream()
                .filter(Objects::nonNull)
                .map(app.goodbuy.ingredients.model.IngredientDTO::of)
                .toList();
    }

    // No toEntity() yet — add when you support create/update flows.
}
