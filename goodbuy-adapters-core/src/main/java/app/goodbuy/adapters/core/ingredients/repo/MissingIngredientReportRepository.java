package app.goodbuy.adapters.core.ingredients.repo;

import app.goodbuy.adapters.core.ingredients.model.MissingIngredientReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MissingIngredientReportRepository
        extends JpaRepository<MissingIngredientReportEntity, Long> {

    /**
     * OLD dedup key for missing-ingredient reports:
     *
     *  - ingredientName
     *  - productEan
     *
     * This is still available for any legacy callers that need it.
     */
    Optional<MissingIngredientReportEntity> findByIngredientNameAndProductEan(
            String ingredientName,
            String productEan
    );

    /**
     * NEW global dedup lookup: one row per ingredientName, regardless of productEan.
     * Service normalizes ingredientName to lower-case before calling this.
     */
    Optional<MissingIngredientReportEntity> findByIngredientNameIgnoreCase(
            String ingredientName
    );
}
