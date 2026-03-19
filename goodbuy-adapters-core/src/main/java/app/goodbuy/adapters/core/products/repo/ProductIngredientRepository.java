package app.goodbuy.adapters.core.products.repo;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.model.ProductIngredientEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ProductIngredientRepository extends JpaRepository<ProductIngredientEntity, Long> {

    /**
     * ⚠️ Default derived query: does NOT fetch-join ingredient (ingredient is LAZY).
     * Keep it if other code depends on it, but do NOT use it for API DTO mapping.
     */
    List<ProductIngredientEntity> findByProduct(ProductEntity product);

    /**
     * ✅ Use this for read paths that must map ingredients into DTOs.
     * Fetch-joins the Ingredient so link.getIngredient() is fully initialized.
     */
    @Query("""
        select pi
        from ProductIngredientEntity pi
        join fetch pi.ingredient i
        where pi.product = :product
        order by pi.id asc
        """)
    List<ProductIngredientEntity> findByProductWithIngredient(@Param("product") ProductEntity product);

    void deleteByProduct(ProductEntity product);

    boolean existsByProductAndIngredient(ProductEntity product, Ingredient ingredient);
}
