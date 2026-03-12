package app.goodbuy.adapters.core.ingredients.service;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientSignalsEntity;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.repository.IngredientSignalsRepository;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentResult;
import app.goodbuy.core.ingredients.scoring.IngredientScoreResult;
import app.goodbuy.core.ingredients.scoring.IngredientScoringEngine;
import app.goodbuy.core.ingredients.scoring.IngredientSignals;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
public class IngredientSignalsWriter {

    private static final Logger log = LoggerFactory.getLogger(IngredientSignalsWriter.class);

    private final IngredientSignalsRepository signalsRepo;
    private final IngredientRepository ingredientRepo;
    private final IngredientScoringEngine scoringEngine = new IngredientScoringEngine();

    public IngredientSignalsWriter(IngredientSignalsRepository signalsRepo, IngredientRepository ingredientRepo) {
        this.signalsRepo = signalsRepo;
        this.ingredientRepo = ingredientRepo;
    }

    @Transactional
    public boolean upsertSignalsAndScore(long ingredientId, IngredientEnrichmentResult enrichment) {
        if (enrichment == null) {
            return false;
        }

        IngredientSignalsEntity entity = signalsRepo.findByIngredientIdForUpdate(ingredientId)
                .orElseGet(() -> new IngredientSignalsEntity(ingredientId));

        entity.setIarcGroup(enrichment.iarcGroup());
        entity.setProp65Listed(Boolean.TRUE.equals(enrichment.prop65Listed()));
        entity.setEwgScore(enrichment.ewgScore());
        entity.setEuProhibited(Boolean.TRUE.equals(enrichment.euProhibited()));
        entity.setEuRestricted(Boolean.TRUE.equals(enrichment.euRestricted()));
        entity.setPubchemMutagen(Boolean.TRUE.equals(enrichment.pubchemMutagen()));
        entity.setPubchemReproductiveToxin(Boolean.TRUE.equals(enrichment.pubchemReproductiveToxin()));
        entity.setEpaChronicToxicity(Boolean.TRUE.equals(enrichment.epaChronicToxicity()));
        entity.setSkinIrritant(Boolean.TRUE.equals(enrichment.skinIrritant()));

        signalsRepo.saveAndFlush(entity);

        boolean signalsExist = signalsRepo.existsById(ingredientId);
        if (!signalsExist) {
            log.error("IngredientSignalsWriter: signals NOT FOUND AFTER FLUSH ingredientId={}", ingredientId);
            return false;
        }

        Ingredient ingredient = ingredientRepo.findByIdForUpdate(ingredientId).orElse(null);
        if (ingredient == null) {
            log.error("IngredientSignalsWriter: ingredient NOT FOUND ingredientId={} (cannot apply derived score)",
                    ingredientId);
            return false;
        }

        IngredientSignals signals = new IngredientSignals(
                entity.getIarcGroup() == null ? java.util.Optional.empty() : java.util.Optional.of((int) entity.getIarcGroup()),
                entity.getEwgScore() == null ? java.util.Optional.empty() : java.util.Optional.of((int) entity.getEwgScore()),
                entity.isProp65Listed(),
                entity.isEuProhibited(),
                entity.isEuRestricted(),
                entity.isPubchemMutagen(),
                entity.isPubchemReproductiveToxin(),
                entity.isEpaChronicToxicity(),
                entity.isSkinIrritant()
        );

        IngredientScoreResult derived = scoringEngine.score(signals);

        ingredient.setSafetyScore(BigDecimal.valueOf(derived.safetyScore()));
        ingredient.setRatingLetter(derived.ratingLetter());

        ingredientRepo.saveAndFlush(ingredient);

        log.info("IngredientSignalsWriter: CONFIRMED ingredientId={} => score={} letter={}",
                ingredientId, derived.safetyScore(), derived.ratingLetter());

        return true;
    }

    @Transactional
    public boolean upsertPubChemSignals(long ingredientId, boolean pubchemMutagen, boolean pubchemReproductiveToxin) {
        return upsertSignalsAndScore(
                ingredientId,
                new IngredientEnrichmentResult(
                        true,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(),
                        List.of(),
                        List.of(),
                        "PUBCHEM",
                        null,
                        "pubchem_only",
                        null,
                        null,
                        null,
                        null,
                        null,
                        pubchemMutagen,
                        pubchemReproductiveToxin,
                        null,
                        null
                )
        );
    }
}
