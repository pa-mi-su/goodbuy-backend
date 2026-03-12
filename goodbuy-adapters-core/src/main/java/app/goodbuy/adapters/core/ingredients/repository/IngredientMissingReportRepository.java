package app.goodbuy.adapters.core.ingredients.repository;

import app.goodbuy.adapters.core.ingredients.model.IngredientMissingReportEntity;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    @Modifying
    @Query(value = """
            insert into ingredient_missing_report (
                ingredient_name,
                product_ean,
                app_version,
                platform,
                notes,
                occurred_at,
                created_at
            ) values (
                :ingredientName,
                :productEan,
                :appVersion,
                :platform,
                :notes,
                :occurredAt,
                :createdAt
            )
            on conflict (ingredient_name, product_ean) do nothing
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("ingredientName") String ingredientName,
            @Param("productEan") String productEan,
            @Param("appVersion") String appVersion,
            @Param("platform") String platform,
            @Param("notes") String notes,
            @Param("occurredAt") Instant occurredAt,
            @Param("createdAt") Instant createdAt
    );

    @Modifying
    @Query(value = """
            update ingredient_missing_report
               set app_version = :appVersion,
                   platform = :platform,
                   notes = :notes,
                   occurred_at = :occurredAt
             where lower(ingredient_name) = lower(:ingredientName)
               and product_ean = :productEan
            """, nativeQuery = true)
    int touchExisting(
            @Param("ingredientName") String ingredientName,
            @Param("productEan") String productEan,
            @Param("appVersion") String appVersion,
            @Param("platform") String platform,
            @Param("notes") String notes,
            @Param("occurredAt") Instant occurredAt
    );
}
