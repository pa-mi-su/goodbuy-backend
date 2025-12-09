package app.goodbuy.adapters.core.products.scoring;

import app.goodbuy.adapters.core.products.model.ProductEntity;
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

/**
 * Spring adapter that:
 *
 *  - Re-loads the ProductEntity from the DB
 *  - Collects ingredient-level scores
 *  - Runs the pure ProductScoringEngine
 *  - WRITES safety_score + rating_letter back to the products table
 *
 * Semantics (🚨 important, matches your UX copy):
 *
 *  - If there is NO ingredient list at all for this product:
 *      -> product.safety_score and rating_letter are set to NULL
 *      -> we return ProductScoreResult with ratingLetter="NR"
 *
 *  - If there IS an ingredient list, but SOME ingredients are missing scores:
 *      -> we DO NOT compute or persist an overall product score
 *      -> product.safety_score and rating_letter are set to NULL
 *      -> we return ProductScoreResult.unrated(...) explaining that we only
 *         have GoodBuy data for X of Y ingredients, so we withhold the score.
 *
 *  - Only when ALL ingredients have scores:
 *      -> we run ProductScoringEngine over the fully-scored list
 *      -> product.safety_score and rating_letter are persisted (A–F, etc.)
 */
@Service
public class ProductScoringAdapterService {

    private static final Logger log = LoggerFactory.getLogger(ProductScoringAdapterService.class);

    private final ProductScoringEngine engine = new ProductScoringEngine();

    @PersistenceContext
    private EntityManager em;

    /**
     * Compute and persist score for a single product.
     */
    @Transactional
    public ProductScoreResult scoreProduct(ProductEntity product) {

        if (product == null || product.getId() == null) {
            throw new IllegalArgumentException("scoreProduct: product or id was null");
        }

        // Get a fresh, managed instance
        ProductEntity managed = em.find(ProductEntity.class, product.getId());
        if (managed == null) {
            throw new IllegalArgumentException("scoreProduct: product id=" + product.getId() + " not found");
        }

        // ─────────────────────────────────────
        // Case 1: NO ingredient list at all → UNRATED
        // ─────────────────────────────────────
        if (managed.getProductIngredients() == null || managed.getProductIngredients().isEmpty()) {
            managed.setSafetyScore(null);
            managed.setRatingLetter(null);

            ProductScoreResult result = ProductScoreResult.unrated(
                    "No ingredient list available for this product."
            );

            log.info(
                    "Product scoring: id={} ean={} -> UNRATED (no ingredient list)",
                    managed.getId(),
                    managed.getEan()
            );

            return result; // managed entity will flush on tx commit
        }

        int totalIngredients = managed.getProductIngredients().size();
        List<IngredientScoreResult> ingredientScores = new ArrayList<>();
        int scoredCount = 0;

        // Collect ONLY fully-scored ingredients
        managed.getProductIngredients().forEach(link -> {
            var ing = link.getIngredient();
            if (ing == null) {
                return;
            }

            if (ing.getSafetyScore() == null || ing.getRatingLetter() == null) {
                // Missing ingredient score → counted as "unscored", but we do NOT fake a 50/C anymore.
                return;
            }

            ingredientScores.add(
                    new IngredientScoreResult(
                            ing.getSafetyScore().intValue(),
                            ing.getRatingLetter(),
                            List.of()
                    )
            );
        });

        scoredCount = ingredientScores.size();

        // ─────────────────────────────────────
        // Case 2: We have ingredients, but ZERO have scores → UNRATED
        // ─────────────────────────────────────
        if (scoredCount == 0) {
            managed.setSafetyScore(null);
            managed.setRatingLetter(null);

            ProductScoreResult result = ProductScoreResult.unrated(
                    "No ingredient scores available yet for this product."
            );

            log.info(
                    "Product scoring: id={} ean={} -> UNRATED (0 of {} ingredients have scores)",
                    managed.getId(),
                    managed.getEan(),
                    totalIngredients
            );

            return result;
        }

        // ─────────────────────────────────────
        // Case 3: Partial coverage (some scored, some not) → UNRATED
        // This matches your UX: show ingredient-level info, BUT
        // DO NOT attach an overall product score until we have them ALL.
        // ─────────────────────────────────────
        if (scoredCount < totalIngredients) {
            managed.setSafetyScore(null);
            managed.setRatingLetter(null);

            String msg = String.format(
                    "We only have GoodBuy data for %d of %d ingredients; " +
                            "until we have them all, we won't attach an overall safety score.",
                    scoredCount,
                    totalIngredients
            );

            ProductScoreResult result = ProductScoreResult.unrated(msg);

            log.info(
                    "Product scoring: id={} ean={} -> UNRATED (partial coverage {}/{})",
                    managed.getId(),
                    managed.getEan(),
                    scoredCount,
                    totalIngredients
            );

            return result;
        }

        // ─────────────────────────────────────
        // Case 4: FULL coverage → compute and persist real product score
        // ─────────────────────────────────────
        ProductScoreResult result = engine.score(ingredientScores);

        managed.setSafetyScore(BigDecimal.valueOf(result.safetyScore()));
        managed.setRatingLetter(result.ratingLetter());

        log.info(
                "Product scoring: id={} ean={} -> score={} grade={} (full coverage {}/{})",
                managed.getId(),
                managed.getEan(),
                result.safetyScore(),
                result.ratingLetter(),
                scoredCount,
                totalIngredients
        );

        return result;
    }

    /**
     * Batch: recompute and persist scores for ALL products.
     *
     * Honors the same semantics as scoreProduct(...):
     *  - no ingredient list      → UNRATED (NULL columns)
     *  - partial ingredient data → UNRATED (NULL columns)
     *  - full ingredient data    → real score + letter persisted
     *
     * @return number of products that were processed (rated or unrated)
     */
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
                // Don't blow up the entire batch if one product is bad.
                log.error("ProductScoringAdapterService: failed to rescore product id={}", p.getId(), ex);
            }
        }

        log.info("ProductScoringAdapterService: completed batch product rescoring; processed {} products", processed);
        return processed;
    }
}
