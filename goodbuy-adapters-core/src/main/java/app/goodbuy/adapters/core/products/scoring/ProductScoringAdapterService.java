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
 * Now also supports a batch operation to recalc ALL products.
 */
@Service
public class ProductScoringAdapterService {

    private static final Logger log = LoggerFactory.getLogger(ProductScoringAdapterService.class);

    private final ProductScoringEngine engine = new ProductScoringEngine();

    @PersistenceContext
    private EntityManager em;

    /**
     * Compute and persist score for a single product.
     *
     * IMPORTANT:
     *  - We DO NOT trust whatever ProductEntity instance the caller gives us.
     *  - We re-load the product from the DB so that productIngredients reflects
     *    the newly-inserted rows in product_ingredients.
     *  - We persist the resulting safety_score + rating_letter back onto the product row.
     *
     * Semantics:
     *  - If there is NO ingredient list at all for this product:
     *      -> product.safety_score and rating_letter are set to NULL
     *      -> we return ProductScoreResult with ratingLetter="NR"
     *  - If there IS an ingredient list, but some ingredients are missing scores:
     *      -> we use a neutral-ish fallback (50 / C) for those ingredients
     *      -> the engine still returns a rated product (A–F)
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
        // If there are NO product_ingredients rows at all:
        //  -> this product is UNRATED ("NR").
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

            // Managed entity will flush at tx commit
            return result;
        }

        List<IngredientScoreResult> ingredientScores = new ArrayList<>();

        managed.getProductIngredients().forEach(link -> {
            var ing = link.getIngredient();

            // Default protection if ingredient has no score yet
            if (ing.getSafetyScore() == null || ing.getRatingLetter() == null) {
                ingredientScores.add(
                        new IngredientScoreResult(
                                50,
                                "C",
                                List.of("No ingredient score available")
                        )
                );
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

        // Run pure engine (we know ingredientScores is non-empty here)
        ProductScoreResult result = engine.score(ingredientScores);

        // Persist back onto the managed ProductEntity
        // (Engine currently only returns rated scores when list is non-empty.)
        managed.setSafetyScore(BigDecimal.valueOf(result.safetyScore()));
        managed.setRatingLetter(result.ratingLetter());

        log.info(
                "Product scoring: id={} ean={} -> score={} grade={}",
                managed.getId(),
                managed.getEan(),
                result.safetyScore(),
                result.ratingLetter()
        );

        // No explicit save() needed – managed entity will flush at tx commit
        return result;
    }

    /**
     * Batch: recompute and persist scores for ALL products.
     *
     * This is the "do it right" batch job:
     *  - loads products from the DB,
     *  - for each product, calls scoreProduct(...),
     *  - writes safety_score + rating_letter back to the products table.
     *
     * Later you can:
     *  - Narrow this to CLEANING domain only (see TODO below),
     *  - Hang an admin endpoint or CLI command on top of this.
     *
     * @return number of products that were processed (rated or unrated)
     */
    @Transactional
    public int recalcScoresForAllProducts() {
        log.info("ProductScoringAdapterService: starting batch product rescoring for ALL products…");

        // TODO (optional): If you have a domain field and want only CLEANING products,
        // replace this with e.g.:
        //
        //   SELECT p FROM ProductEntity p WHERE p.domain = :domain
        //
        // and setParameter("domain", ProductDomain.CLEANING)
        //
        TypedQuery<ProductEntity> query = em.createQuery(
                "SELECT p FROM ProductEntity p",
                ProductEntity.class
        );

        List<ProductEntity> products = query.getResultList();
        log.info("ProductScoringAdapterService: loaded {} products for rescoring", products.size());

        int processed = 0;

        for (ProductEntity p : products) {
            try {
                // This will re-load the product by ID and persist the new score/letter or NR.
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
