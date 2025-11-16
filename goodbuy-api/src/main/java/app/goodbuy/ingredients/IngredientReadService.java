package app.goodbuy.ingredients;

import app.goodbuy.adapters.core.ingredients.IngredientMapper;
import app.goodbuy.adapters.core.ingredients.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.pubchem.PubChemClient;
import app.goodbuy.adapters.core.ingredients.pubchem.PubChemMapper;
import app.goodbuy.adapters.core.ingredients.pubchem.PubChemRaw;
import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.ingredients.port.IngredientReadPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IngredientReadService {

    private final IngredientReadPort port;
    private final IngredientRepository repository;
    private final IngredientMapper mapper;
    private final PubChemClient pubChemClient;
    private final PubChemMapper pubChemMapper;

    public IngredientReadService(
            IngredientReadPort port,
            IngredientRepository repository,
            IngredientMapper mapper,
            PubChemClient pubChemClient,
            PubChemMapper pubChemMapper
    ) {
        this.port = port;
        this.repository = repository;
        this.mapper = mapper;
        this.pubChemClient = pubChemClient;
        this.pubChemMapper = pubChemMapper;
    }

    /**
     * 1) Try DB through IngredientReadPort (existing behavior)
     * 2) If not found → query PubChem → map → save → return DTO
     */
    @Transactional
    public Optional<IngredientDTO> findByNameOrAlias(String nameOrAlias) {
        if (nameOrAlias == null || nameOrAlias.trim().isEmpty()) {
            return Optional.empty();
        }

        String key = normalize(nameOrAlias);

        // STEP 1 — existing behavior via port
        Optional<IngredientDTO> existing = port.findByNameOrAlias(key);
        if (existing.isPresent()) {
            return existing;
        }

        // STEP 2 — DB miss → try PubChem
        PubChemRaw pc = pubChemClient.fetch(key);
        if (pc.isError() || !pc.isOk()) {
            // No fallback data available
            return Optional.empty();
        }

        // STEP 3 — Map PubChem → Ingredient entity
        Ingredient newIng = pubChemMapper.mapToIngredient(key, pc);

        // STEP 4 — Save new ingredient
        Ingredient saved = repository.save(newIng);

        // STEP 5 — Return DTO
        return Optional.of(mapper.toDto(saved));
    }

    /**
     * Batch lookup: use port as before. No PubChem fallback for lists (V1).
     */
    public List<IngredientDTO> findManyByNamesOrAliases(Collection<String> names) {
        return port.findManyByNamesOrAliases(names);
    }

    private String normalize(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }
}
