package app.goodbuy.adapters.core.ingredients;

import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.ingredients.port.IngredientReadPort;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class CoreIngredientReadAdapter implements IngredientReadPort {

    private final IngredientRepository repository;
    private final IngredientMapper mapper;

    public CoreIngredientReadAdapter(IngredientRepository repository, IngredientMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<IngredientDTO> findByNameOrAlias(String nameOrAlias) {
        if (nameOrAlias == null) {
            return Optional.empty();
        }

        String needle = normalize(nameOrAlias);
        if (needle.isEmpty()) {
            return Optional.empty();
        }

        return repository.findByNameOrAlias(needle)
                .map(mapper::toDto);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IngredientDTO> findManyByNamesOrAliases(Collection<String> names) {
        if (names == null || names.isEmpty()) {
            return List.of();
        }

        List<String> normalized = names.stream()
                .filter(Objects::nonNull)
                .map(this::normalize)
                .filter(s -> !s.isEmpty())
                .distinct()
                .collect(Collectors.toList());

        if (normalized.isEmpty()) {
            return List.of();
        }

        return mapper.toDtoList(repository.findManyByNamesOrAliases(normalized));
    }

    private String normalize(String s) {
        return s.trim().toLowerCase(Locale.ROOT);
    }
}
