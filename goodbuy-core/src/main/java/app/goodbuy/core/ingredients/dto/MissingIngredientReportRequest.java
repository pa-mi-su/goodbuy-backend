package app.goodbuy.core.ingredients.dto;

import java.time.OffsetDateTime;

/**
 * Request payload sent by the mobile app when a user taps
 * "We don't have this ingredient, notify us".
 *
 * This is intentionally backend-agnostic (no JPA, no framework annotations).
 * Validation can be applied in the API layer.
 */
public record MissingIngredientReportRequest(
        String ingredientName,  // exact label the user saw
        String productEan,      // optional: product EAN/GTIN, if known
        String appVersion,      // optional: "iOS 1.0.3" etc.
        String platform,        // optional: "iOS", "Android", etc.
        String notes,           // optional: any extra text from user
        OffsetDateTime occurredAt // optional: client-side timestamp, may be null
) {}
