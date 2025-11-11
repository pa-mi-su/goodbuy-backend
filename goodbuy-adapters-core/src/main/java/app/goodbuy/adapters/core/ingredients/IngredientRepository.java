package app.goodbuy.adapters.core.ingredients;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    @EntityGraph(attributePaths = "aliases")
    @Query("""
        select i
        from Ingredient i
        left join fetch i.aliases a
        where lower(i.canonicalKey) = :needle
           or lower(i.displayName) = :needle
           or lower(a.name) = :needle
        """)
    Optional<Ingredient> findByNameOrAlias(@Param("needle") String needle);

    @EntityGraph(attributePaths = "aliases")
    @Query("""
        select distinct i
        from Ingredient i
        left join i.aliases a
        where lower(i.canonicalKey) in :needles
           or lower(i.displayName) in :needles
           or lower(a.name) in :needles
        """)
    List<Ingredient> findManyByNamesOrAliases(@Param("needles") Collection<String> needles);
}
