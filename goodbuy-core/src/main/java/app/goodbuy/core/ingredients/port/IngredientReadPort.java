package app.goodbuy.core.ingredients.port;

import app.goodbuy.core.ingredients.dto.IngredientDTO;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Core port for reading ingredients.
 * Implemented in the API module (e.g. via JPA).
 */
public interface IngredientReadPort {

    /**
     * Find a single ingredient by canonical name or alias.
     */
    Optional<IngredientDTO> findByNameOrAlias(String nameOrAlias);

    /**
     * Find many ingredients by a collection of names/aliases.
     */
    List<IngredientDTO> findManyByNamesOrAliases(Collection<String> names);
}
