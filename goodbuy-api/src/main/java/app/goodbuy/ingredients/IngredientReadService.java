package app.goodbuy.ingredients;

import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.ingredients.port.IngredientReadPort;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Service
public class IngredientReadService {

    private final IngredientReadPort port;

    public IngredientReadService(IngredientReadPort port) {
        this.port = port;
    }

    public Optional<IngredientDTO> findByNameOrAlias(String nameOrAlias) {
        return port.findByNameOrAlias(nameOrAlias);
    }

    public List<IngredientDTO> findManyByNamesOrAliases(Collection<String> names) {
        return port.findManyByNamesOrAliases(names);
    }
}
