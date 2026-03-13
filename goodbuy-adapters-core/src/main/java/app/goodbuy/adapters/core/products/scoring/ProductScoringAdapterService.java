package app.goodbuy.adapters.core.products.scoring;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientSignalsEntity;
import app.goodbuy.adapters.core.ingredients.repository.IngredientSignalsRepository;
import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.core.ingredients.scoring.IngredientScoringEngine;
import app.goodbuy.core.ingredients.scoring.IngredientSignals;
import app.goodbuy.core.ingredients.scoring.IngredientScoreResult;
import app.goodbuy.core.products.scoring.ProductScoreResult;
import app.goodbuy.core.products.scoring.ProductScoringEngine;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class ProductScoringAdapterService {

    private static final Logger log = LoggerFactory.getLogger(ProductScoringAdapterService.class);

    private final ProductScoringEngine engine = new ProductScoringEngine();
    private final IngredientScoringEngine ingredientScoringEngine = new IngredientScoringEngine();
    private final IngredientSignalsRepository signalsRepo;

    @PersistenceContext
    private EntityManager em;

    public ProductScoringAdapterService(IngredientSignalsRepository signalsRepo) {
        this.signalsRepo = signalsRepo;
    }

    @Transactional
    public ProductScoreResult scoreProduct(ProductEntity product) {

        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("scoreProduct: product or id was null");
        }

        ProductEntity managed = em.find(ProductEntity.class, product.getId());
        if (managed == null) {
            throw new IllegalArgumentException("scoreProduct: product id=" + product.getId() + " not found");
        }

        if (managed.getProductIngredients() == null || managed.getProductIngredients().isEmpty()) {
            managed.setSafetyScore(null);
            managed.setRatingLetter(null);

            log.info("Product scoring: id={} ean={} -> UNRATED (no ingredient list)",
                    managed.getId(), managed.getEan());

            return ProductScoreResult.unrated("No ingredient list available for this product.");
        }

        int totalIngredients = managed.getProductIngredients().size();
        List<IngredientScoreResult> ingredientScores = new ArrayList<>();

        managed.getProductIngredients().forEach(link -> {
            Ingredient ing = link.getIngredient();
            if (ing == null) return;

            // If ingredient lacks score, try to derive from signals (safety net)
            if (ing.getSafetyScore() == null || ing.getRatingLetter() == null) {
                boolean derived = tryDeriveAndPersistIngredientScoreFromSignals(ing);
                if (!derived) {
                    return; // still unscored
                }
            }

            ingredientScores.add(new IngredientScoreResult(
                    ing.getSafetyScore().intValue(),
                    ing.getRatingLetter(),
                    List.of()
            ));
        });

        int scoredCount = ingredientScores.size();

        if (scoredCount == 0) {
            managed.setSafetyScore(null);
            managed.setRatingLetter(null);

            log.info("Product scoring: id={} ean={} -> UNRATED (0 of {} ingredients have scores)",
                    managed.getId(), managed.getEan(), totalIngredients);

            return ProductScoreResult.unrated("No ingredient scores available yet for this product.");
        }

        double coverage = scoredCount / (double) totalIngredients;
        double requiredCoverage = requiredCoverageFor(managed.getDomain());

        if (coverage < requiredCoverage) {
            managed.setSafetyScore(null);
            managed.setRatingLetter(null);

            log.info("Product scoring: id={} ean={} -> UNRATED (coverage {}/{} below threshold {})",
                    managed.getId(), managed.getEan(), scoredCount, totalIngredients, requiredCoverage);

            return ProductScoreResult.unrated(String.format(
                    "We have guidance for %d of %d ingredients. We need a bit more coverage before showing an overall product score.",
                    scoredCount, totalIngredients
            ));
        }

        ProductScoreResult result = engine.score(ingredientScores, managed.getDomain());

        managed.setSafetyScore(BigDecimal.valueOf(result.safetyScore()));
        managed.setRatingLetter(result.ratingLetter());

        log.info("Product scoring: id={} ean={} -> score={} grade={} (full coverage {}/{})",
                managed.getId(), managed.getEan(),
                result.safetyScore(), result.ratingLetter(),
                scoredCount, totalIngredients);

        return result;
    }

    private double requiredCoverageFor(String domain) {
        if (domain == null || domain.isBlank()) {
            return 0.80d;
        }
        String normalized = domain.trim().toLowerCase(java.util.Locale.ROOT);
        if (normalized.contains("cleaning")) {
            return 0.70d;
        }
        if (normalized.contains("vitamin")
                || normalized.contains("supplement")
                || normalized.contains("food")
                || normalized.contains("baby")) {
            return 0.85d;
        }
        return 0.80d;
    }

    @Transactional
    public int recalcScoresForAllProducts() {
        log.info("ProductScoringAdapterService: starting batch product rescoring for ALL products…");

        TypedQuery<ProductEntity> query = em.createQuery(
                "SELECT p FROM ProductEntity p",
                ProductEntity.class
        );

        List<ProductEntity> products = query.getResultList();
        log.info("ProductScoringAdapterService: loaded {} products for rescoring", products.size());

        int processed = 0;

        for (ProductEntity p : products) {
            try {
                scoreProduct(p);
                processed++;
            } catch (Exception ex) {
                log.error("ProductScoringAdapterService: failed to rescore product id={}", p.getId(), ex);
            }
        }

        log.info("ProductScoringAdapterService: completed batch product rescoring; processed {} products", processed);
        return processed;
    }

    @Transactional
    public Optional<ProductScoreResult> rescoreByEan(String ean) {
        if (ean == null || ean.isBlank()) {
            return Optional.empty();
        }

        TypedQuery<ProductEntity> query = em.createQuery(
                "SELECT p FROM ProductEntity p WHERE p.ean = :ean",
                ProductEntity.class
        );
        query.setParameter("ean", ean.trim());
        List<ProductEntity> products = query.getResultList();
        if (products.isEmpty()) {
            return Optional.empty();
        }

        return Optional.of(scoreProduct(products.get(0)));
    }

    /**
     * Safety net: if Ingredient.safety_score/rating_letter are null, but signals exist,
     * derive a score and persist it using the managed Ingredient entity.
     */
    private boolean tryDeriveAndPersistIngredientScoreFromSignals(Ingredient ing) {
        if (ing.getId() == null) return false;

        IngredientSignalsEntity s = signalsRepo.findById(ing.getId()).orElse(null);
        IngredientSignals signals = s == null
                ? IngredientSignals.empty()
                : new IngredientSignals(
                        s.getIarcGroup() == null ? java.util.Optional.empty() : java.util.Optional.of((int) s.getIarcGroup()),
                        s.getEwgScore() == null ? java.util.Optional.empty() : java.util.Optional.of((int) s.getEwgScore()),
                        s.isProp65Listed(),
                        s.isEuProhibited(),
                        s.isEuRestricted(),
                        s.isPubchemMutagen(),
                        s.isPubchemReproductiveToxin(),
                        s.isEpaChronicToxicity(),
                        s.isSkinIrritant()
                );

        IngredientScoreResult derived = ingredientScoringEngine.score(ing.getCanonicalKey(), signals);
        if (!derived.isRated()) {
            return false;
        }

        ing.setSafetyScore(BigDecimal.valueOf(derived.safetyScore()));
        ing.setRatingLetter(derived.ratingLetter());

        // No explicit save() needed: ing is managed via ProductEntity graph in this TX.
        // But forcing flush helps you see it immediately.
        em.flush();

        log.info("Ingredient score derived ingredientId={} canonicalKey='{}' => score={} letter={}",
                ing.getId(), ing.getCanonicalKey(), derived.safetyScore(), derived.ratingLetter());

        return true;
    }
}
