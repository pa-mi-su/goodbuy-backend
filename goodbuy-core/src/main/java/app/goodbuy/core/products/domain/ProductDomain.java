package app.goodbuy.core.products.domain;

import java.util.Locale;

/**
 * High-level domains GoodBuy cares about.
 *
 * IMPORTANT:
 * Support/rating is NOT hard-coded here.
 * It is DB-driven via product_domain_config.
 *
 * This enum only provides stable string codes for persistence/DTOs.
 */
public enum ProductDomain {

    VITAMINS("vitamins"),
    CLEANING("cleaning"),
    BABY("baby"),
    FOOD("food"),
    OTHER("other"),
    UNKNOWN("unknown");

    private final String code;

    ProductDomain(String code) {
        this.code = code;
    }

    /** Stable string used in DB + over the wire (DTOs). */
    public String code() {
        return code;
    }

    /** Parse from a DB / DTO string, defaulting to UNKNOWN. */
    public static ProductDomain fromCode(String raw) {
        if (raw == null || raw.isBlank()) return UNKNOWN;

        String normalized = raw.trim().toLowerCase(Locale.ROOT);
        for (ProductDomain d : values()) {
            if (d.code.equals(normalized)) return d;
        }
        return UNKNOWN;
    }
}
