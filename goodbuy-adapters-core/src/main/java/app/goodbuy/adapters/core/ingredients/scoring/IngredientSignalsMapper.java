package app.goodbuy.adapters.core.ingredients.scoring;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientSignalsEntity;
import app.goodbuy.adapters.core.ingredients.repository.IngredientSignalsRepository;
import app.goodbuy.core.ingredients.scoring.IngredientSignals;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Maps DB Ingredient + IngredientSignalsEntity into IngredientSignals
 * used by the pure IngredientScoringEngine.
 *
 * NOW:
 *   - We read the ingredient_signals row (if present) via IngredientSignalsRepository.
 *   - If found, we convert it into a core IngredientSignals record.
 *   - If not found, we return a neutral "no hazard data" signal set.
 *
 * LATER:
 *   - PubChem / Prop65 / EWG / etc. jobs will populate ingredient_signals
 *     so this mapper starts reflecting real external data automatically.
 */
@Component
public class IngredientSignalsMapper {

    private final IngredientSignalsRepository signalsRepository;

    public IngredientSignalsMapper(IngredientSignalsRepository signalsRepository) {
        this.signalsRepository = signalsRepository;
    }

    /**
     * Build IngredientSignals from the DB for a given Ingredient.
     *
     * If there is no ingredient_signals row yet, we return IngredientSignals.empty().
     */
    public IngredientSignals fromIngredient(Ingredient ingredient) {
        if (ingredient == null || ingredient.getId() == null) {
            return IngredientSignals.empty();
        }

        return signalsRepository.findById(ingredient.getId())
                .map(this::toCoreSignals)
                .orElseGet(IngredientSignals::empty);
    }

    private IngredientSignals toCoreSignals(IngredientSignalsEntity entity) {
        return new IngredientSignals(
                Optional.ofNullable(entity.getIarcGroup()),
                Optional.ofNullable(entity.getEwgScore()),
                entity.isProp65Listed(),
                entity.isEuProhibited(),
                entity.isEuRestricted(),
                entity.isPubchemMutagen(),
                entity.isPubchemReproductiveToxin(),
                entity.isEpaChronicToxicity(),
                entity.isSkinIrritant()
        );
    }
}
