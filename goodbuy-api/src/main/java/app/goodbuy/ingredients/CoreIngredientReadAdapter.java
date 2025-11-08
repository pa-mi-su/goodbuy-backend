package app.goodbuy.ingredients;

import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.ingredients.port.IngredientReadPort;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Adapter that plugs the core IngredientReadPort into the API layer's
 * IngredientReadService / JPA stack.
 *
 * Core depends on this PORT.
 * API implements it and is wired via Spring.
 */
@Component
public class CoreIngredientReadAdapter implements IngredientReadPort {

    private final IngredientReadService service;

    public CoreIngredientReadAdapter(IngredientReadService service) {
        this.service = service;
    }

    @Override
    public Optional<IngredientDTO> findByNameOrAlias(String nameOrAlias) {
        return service.findByNameOrAlias(nameOrAlias);
    }

    @Override
    public List<IngredientDTO> findManyByNamesOrAliases(Collection<String> names) {
        return service.findManyByNamesOrAliases(names);
    }
}
