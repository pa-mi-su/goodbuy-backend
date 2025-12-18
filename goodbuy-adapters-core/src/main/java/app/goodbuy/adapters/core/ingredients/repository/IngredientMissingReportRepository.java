package app.goodbuy.adapters.core.ingredients.repository;

import app.goodbuy.adapters.core.ingredients.model.IngredientMissingReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IngredientMissingReportRepository
        extends JpaRepository<IngredientMissingReportEntity, Long> {

    /**
     * One row per (ingredient_name, product_ean) enforced via:
     *  UNIQUE (ingredient_name, product_ean)
     *
     * This method lets MissingIngredientReportService check if a report
     * already exists (case-insensitive on ingredient_name) before inserting
     * a new one or deciding whether to send Slack.
     */
    Optional<IngredientMissingReportEntity> findByIngredientNameIgnoreCaseAndProductEan(
            String ingredientName,
            String productEan
    );
}
