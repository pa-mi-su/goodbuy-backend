package app.goodbuy.adapters.core.products.repo;

import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.model.ProductIngredientEntity;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProductIngredientRepository extends JpaRepository<ProductIngredientEntity, Long> {

    List<ProductIngredientEntity> findByProduct(ProductEntity product);

    void deleteByProduct(ProductEntity product);

    boolean existsByProductAndIngredient(ProductEntity product, Ingredient ingredient);
}
