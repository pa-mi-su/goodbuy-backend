package app.goodbuy.adapters.core.citations.repo;

import app.goodbuy.adapters.core.citations.model.IngredientCitationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IngredientCitationRepository extends JpaRepository<IngredientCitationEntity, Long> {
    boolean existsByIngredientIdAndCitationId(Long ingredientId, Long citationId);
}
