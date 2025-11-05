package app.goodbuy.ingredients;

import app.goodbuy.ingredients.model.Ingredient;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface IngredientRepository extends JpaRepository<Ingredient, Long> {

    @EntityGraph(attributePaths = {"aliases"})
    @Query("""
        select distinct i from Ingredient i
        left join i.aliases a
        where lower(i.canonicalKey) = ?1
           or lower(i.displayName) = ?1
           or lower(a.alias) = ?1
        """)
    Optional<Ingredient> findByNameOrAlias(String needleLower);

    @EntityGraph(attributePaths = {"aliases"})
    @Query("""
        select distinct i from Ingredient i
        left join i.aliases a
        where lower(i.canonicalKey) in (?1)
           or lower(i.displayName) in (?1)
           or lower(a.alias) in (?1)
        """)
    List<Ingredient> findManyByNamesOrAliases(Collection<String> needlesLower);
}
