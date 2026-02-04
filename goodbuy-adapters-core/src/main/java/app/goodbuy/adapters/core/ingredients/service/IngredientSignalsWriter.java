package app.goodbuy.adapters.core.ingredients.service;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientSignalsEntity;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.repository.IngredientSignalsRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

@Service
public class IngredientSignalsWriter {

    private static final Logger log = LoggerFactory.getLogger(IngredientSignalsWriter.class);

    private final IngredientSignalsRepository signalsRepo;
    private final IngredientRepository ingredientRepo;

    public IngredientSignalsWriter(IngredientSignalsRepository signalsRepo, IngredientRepository ingredientRepo) {
        this.signalsRepo = signalsRepo;
        this.ingredientRepo = ingredientRepo;
    }

    /**
     * JPA "upsert" (portable, no JDBC):
     * - lock+load row if it exists
     * - if absent, create new row with PK = ingredientId
     * - update flags
     * - flush signals row
     * - THEN update Ingredient.safety_score + rating_letter (Option B)
     *
     * Returns true only when both:
     *  - ingredient_signals row exists after flush
     *  - ingredient row exists and was updated with a derived score
     */
    @Transactional
    public boolean upsertPubChemSignals(long ingredientId, boolean pubchemMutagen, boolean pubchemReproductiveToxin) {

        IngredientSignalsEntity entity = signalsRepo.findByIngredientIdForUpdate(ingredientId)
                .orElseGet(() -> new IngredientSignalsEntity(ingredientId));

        entity.setPubchemMutagen(pubchemMutagen);
        entity.setPubchemReproductiveToxin(pubchemReproductiveToxin);

        // 1) Force SQL for signals now
        signalsRepo.saveAndFlush(entity);

        boolean signalsExist = signalsRepo.existsById(ingredientId);
        if (!signalsExist) {
            log.error("IngredientSignalsWriter: signals NOT FOUND AFTER FLUSH ingredientId={} mutagen={} reproToxin={}",
                    ingredientId, pubchemMutagen, pubchemReproductiveToxin);
            return false;
        }

        // 2) Option B: write derived score/letter into ingredients
        Ingredient ingredient = ingredientRepo.findByIdForUpdate(ingredientId).orElse(null);
        if (ingredient == null) {
            log.error("IngredientSignalsWriter: ingredient NOT FOUND ingredientId={} (cannot apply derived score)",
                    ingredientId);
            return false;
        }

        DerivedScore derived = deriveFromPubChem(pubchemMutagen, pubchemReproductiveToxin);

        ingredient.setSafetyScore(BigDecimal.valueOf(derived.score()));
        ingredient.setRatingLetter(derived.letter());

        ingredientRepo.saveAndFlush(ingredient);

        log.info("IngredientSignalsWriter: CONFIRMED ingredientId={} mutagen={} reproToxin={} => score={} letter={}",
                ingredientId, pubchemMutagen, pubchemReproductiveToxin, derived.score(), derived.letter());

        return true;
    }

    /**
     * Centralized scoring rule for PubChem flags (matches your SQL test):
     * - if mutagen OR reproductive_toxin => 80 / D
     * - else => 20 / A
     */
    private static DerivedScore deriveFromPubChem(boolean mutagen, boolean reproToxin) {
        if (mutagen || reproToxin) {
            return new DerivedScore(80, "D");
        }
        return new DerivedScore(20, "A");
    }

    private record DerivedScore(int score, String letter) {}
}
