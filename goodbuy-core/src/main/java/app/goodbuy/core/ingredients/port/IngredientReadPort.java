package app.goodbuy.core.ingredients.port;

import app.goodbuy.core.ingredients.dto.IngredientDTO;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredientReadPort {

    Optional<IngredientDTO> findByNameOrAlias(String nameOrAlias);

    List<IngredientDTO> findManyByNamesOrAliases(Collection<String> names);
}
