package app.goodbuy.adapters.core.ingredients.repository;

import app.goodbuy.adapters.core.ingredients.model.IngredientSignalsEntity;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

/**
 * JPA repository for IngredientSignalsEntity.
 *
 * Stores per-ingredient hazard/signal flags (ingredient_signals table).
 * Primary key is ingredient_id.
 */
public interface IngredientSignalsRepository extends JpaRepository<IngredientSignalsEntity, Long> {

    /**
     * Explicit convenience alias for readability.
     * (JpaRepository already provides findById.)
     */
    Optional<IngredientSignalsEntity> findByIngredientId(Long ingredientId);

    /**
     * Lock the row for update if it exists.
     * Use this inside a @Transactional writer to reduce concurrent upsert races.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from IngredientSignalsEntity s where s.ingredientId = :ingredientId")
    Optional<IngredientSignalsEntity> findByIngredientIdForUpdate(@Param("ingredientId") Long ingredientId);
}
