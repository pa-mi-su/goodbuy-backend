package app.goodbuy.core.ingredients.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Pure domain representation of an ingredient.
 * This version is framework-agnostic — no JPA, no Jackson, no entity dependencies.
 * Used for communication across modules (ports/adapters/API).
 */
public record IngredientDTO(
        Long id,
        String canonicalKey,
        String displayName,
        String summary,
        String description,
        String func,
        String concerns,
        BigDecimal safetyScore,
        String ratingLetter,
        Integer referencesCount,
        String category,
        String regulationNotes,
        boolean isActive,
        List<String> tags,
        List<String> aliases,
        OffsetDateTime updatedAt
) {}
