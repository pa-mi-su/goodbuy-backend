package app.goodbuy.adapters.core.ingredients.repository;

import app.goodbuy.adapters.core.ingredients.model.IngredientSignalsEntity;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * JPA repository for IngredientSignalsEntity.
 *
 * Allows adapters to read/write hazard signal data
 * that feeds the core IngredientScoringEngine.
 */
public interface IngredientSignalsRepository
        extends JpaRepository<IngredientSignalsEntity, Long> {
}
