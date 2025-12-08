package app.goodbuy.adapters.core.ingredients.repository;

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
           or lower(a.alias) = :needle
        """)
    Optional<Ingredient> findByNameOrAlias(@Param("needle") String needle);

    @EntityGraph(attributePaths = "aliases")
    @Query("""
        select distinct i
        from Ingredient i
        left join i.aliases a
        where lower(i.canonicalKey) in :needles
           or lower(i.displayName) in :needles
           or lower(a.alias) in :needles
        """)
    List<Ingredient> findManyByNamesOrAliases(@Param("needles") Collection<String> needles);

    /**
     * Direct lookup by canonicalKey (case-insensitive).
     *
     * Used by DbProductSnapshotAdapter when wiring ingredient links
     * from EAN-DB ingredient names into our GoodBuy ingredients table.
     */
    Optional<Ingredient> findByCanonicalKeyIgnoreCase(String canonicalKey);

    /**
     * Exact alias match (case-insensitive).
     *
     * Used by the ingredient search API as the second tier after canonical_key.
     */
    @EntityGraph(attributePaths = "aliases")
    @Query("""
        select distinct i
        from Ingredient i
        join i.aliases a
        where lower(a.alias) = lower(:alias)
        """)
    List<Ingredient> findByAliasExact(@Param("alias") String alias);

    /**
     * Loose search across displayName, canonicalKey and aliases.
     *
     * Used as a fallback when we don't get a canonical_key or exact-alias hit.
     */
    @EntityGraph(attributePaths = "aliases")
    @Query("""
        select distinct i
        from Ingredient i
        left join i.aliases a
        where lower(i.displayName) like lower(concat('%', :q, '%'))
           or lower(i.canonicalKey) like lower(concat('%', :q, '%'))
           or lower(a.alias) like lower(concat('%', :q, '%'))
        """)
    List<Ingredient> searchLoose(@Param("q") String q);

    // ─────────────────────────────────────────────────────────────
    // Ranked search helpers used by IngredientReadService
    // ─────────────────────────────────────────────────────────────

    /**
     * All candidates for a single normalized needle (lowercased).
     * We keep it broad; ranking is done in IngredientReadService.
     */
    @EntityGraph(attributePaths = "aliases")
    @Query("""
        select distinct i
        from Ingredient i
        left join i.aliases a
        where lower(i.canonicalKey) = :needle
           or lower(i.displayName) = :needle
           or lower(a.alias) = :needle
        """)
    List<Ingredient> findByAllNormalized(@Param("needle") String needle);

    /**
     * All candidates for a batch of normalized needles.
     */
    @EntityGraph(attributePaths = "aliases")
    @Query("""
        select distinct i
        from Ingredient i
        left join i.aliases a
        where lower(i.canonicalKey) in :needles
           or lower(i.displayName) in :needles
           or lower(a.alias) in :needles
        """)
    List<Ingredient> findManyByAllNormalized(@Param("needles") List<String> needles);
}
