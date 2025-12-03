package app.goodbuy.core.products.dto;

import java.math.BigDecimal;

public record ProductIngredientViewDto(
        String name,
        String canonicalKey,
        boolean inCatalog,
        String ratingLetter,
        BigDecimal safetyScore
) {}
