package app.goodbuy.adapters.core.ingredients.repo;

import app.goodbuy.adapters.core.ingredients.model.MissingIngredientReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MissingIngredientReportRepository
        extends JpaRepository<MissingIngredientReportEntity, Long> {

    /**
     * Dedup key for missing-ingredient reports:
     *
     *  - ingredientName
     *  - productEan
     *
     * This is the key for our "one row per (ingredient, EAN)" logic.
     * A DB UNIQUE constraint should exist on (ingredient_name, product_ean)
     * to enforce this at the database level as well.
     */
    Optional<MissingIngredientReportEntity> findByIngredientNameAndProductEan(
            String ingredientName,
            String productEan
    );
}
