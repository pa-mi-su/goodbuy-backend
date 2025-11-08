package app.goodbuy.ingredients;

import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.ingredients.model.Ingredient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IngredientReadService {
    private final IngredientRepository repo;
    private final IngredientMapper mapper;

    public IngredientReadService(IngredientRepository repo, IngredientMapper mapper) {
        this.repo = repo;
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public Optional<IngredientDTO> findByNameOrAlias(String nameOrAlias) {
        if (nameOrAlias == null) return Optional.empty();
        String needle = nameOrAlias.trim().toLowerCase(Locale.ROOT);
        if (needle.isEmpty()) return Optional.empty();

        return repo.findByNameOrAlias(needle).map(mapper::toDto);
    }

    @Transactional(readOnly = true)
    public List<IngredientDTO> findManyByNamesOrAliases(Collection<String> names) {
        if (names == null || names.isEmpty()) return List.of();

        var lowered = names.stream()
                .filter(s -> s != null)
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .toList();

        if (lowered.isEmpty()) return List.of();

        List<Ingredient> hits = repo.findManyByNamesOrAliases(lowered);
        return mapper.toDtoList(hits);
    }
}
