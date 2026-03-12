package app.goodbuy.adapters.core.ingredients.scoring;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.core.ingredients.scoring.IngredientScoreResult;
import app.goodbuy.core.ingredients.scoring.IngredientScoringEngine;
import app.goodbuy.core.ingredients.scoring.IngredientSignals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spring adapter that:
 *
 *  - Loads Ingredient entities from the DB
 *  - Maps them to IngredientSignals
 *  - Runs the pure IngredientScoringEngine
 *  - Writes safety_score + rating_letter back to the DB
 *
 * IMPORTANT:
 *  - We ONLY recalc ingredients that do NOT yet have a score+grade.
 *    This lets you manually curate certain ingredients (water, baking soda, etc.)
 *    without the batch job overwriting your values.
 *
 *  - We also SKIP ingredients that have no hazard/regulatory signals at all.
 *    If we don't have any data, we do NOT assume 95/A; we leave the fields NULL.
 */
@Service
public class IngredientScoringAdapterService {

    private static final Logger log = LoggerFactory.getLogger(IngredientScoringAdapterService.class);

    private final IngredientRepository ingredientRepository;
    private final IngredientSignalsMapper signalsMapper;
    private final IngredientScoringEngine engine;

    public IngredientScoringAdapterService(
            IngredientRepository ingredientRepository,
            IngredientSignalsMapper signalsMapper
    ) {
        this.ingredientRepository = ingredientRepository;
        this.signalsMapper = signalsMapper;
        this.engine = new IngredientScoringEngine();
    }

    /**
     * Batch-recalculate scores for all ingredients that do NOT yet have
     * safety_score + rating_letter set AND that have at least one signal.
     *
     * Returns the number of rows that were actually updated.
     */
    @Transactional
    public int recalcScoresForAllIngredients() {
        List<Ingredient> all = ingredientRepository.findAll();
        log.info("Ingredient scoring: starting batch over {} ingredients", all.size());

        int updated = 0;
        int skipped = 0;

        for (Ingredient ing : all) {

            // 🔒 Respect manually curated scores:
            // If BOTH safety_score and rating_letter are already set, skip.
            if (ing.getSafetyScore() != null && ing.getRatingLetter() != null) {
                skipped++;
                continue;
            }

            IngredientSignals signals = signalsMapper.fromIngredient(ing);

            IngredientScoreResult result = engine.score(ing.getCanonicalKey(), signals);

            if (!result.isRated()) {
                log.debug(
                        "Ingredient scoring: skipping id={} canonicalKey='{}' - insufficient authoritative evidence",
                        ing.getId(),
                        ing.getCanonicalKey()
                );
                skipped++;
                continue;
            }

            // Persist result back into the entity
            ing.setSafetyScore(BigDecimal.valueOf(result.safetyScore()));
            ing.setRatingLetter(result.ratingLetter());

            ingredientRepository.save(ing);
            updated++;

            log.debug(
                    "Ingredient scoring: id={} canonicalKey='{}' -> score={} grade={}",
                    ing.getId(),
                    ing.getCanonicalKey(),
                    result.safetyScore(),
                    result.ratingLetter()
            );
        }

        log.info("Ingredient scoring: DONE updated={} skipped={}", updated, skipped);
        return updated;
    }
}
