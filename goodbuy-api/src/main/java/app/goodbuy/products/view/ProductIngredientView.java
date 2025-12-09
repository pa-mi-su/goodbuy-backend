package app.goodbuy.products.view;

import java.math.BigDecimal;

/**
 * Per-ingredient view for the product endpoint.
 * This is what iOS will use to color the leaf:
 *
 *  - name: display label used on the list
 *  - canonicalKey: our internal canonical key (if present)
 *  - inCatalog: true if we found it in GoodBuy DB
 *  - ratingLetter / safetyScore: used for coloring
 */
public record ProductIngredientView(
        String name,
        String canonicalKey,
        boolean inCatalog,
        String ratingLetter,
        BigDecimal safetyScore
) {
}
