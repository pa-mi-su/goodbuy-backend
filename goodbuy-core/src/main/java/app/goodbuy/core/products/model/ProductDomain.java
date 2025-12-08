package app.goodbuy.core.products.model;

/**
 * GoodBuy product "domain" – the big bucket a product belongs to.
 *
 * This is *our* internal classification, not whatever the external
 * catalog calls its categories.
 *
 * We keep a stable string code for persistence / JSON:
 *   - CLEANING  -> "cleaning"
 *   - BABY      -> "baby"
 *   - FOOD      -> "food"
 *   - UNKNOWN   -> "unknown"
 *
 * Record DTOs continue to use String for the domain field; this enum
 * centralizes the allowed values and mappings.
 */
public enum ProductDomain {

    CLEANING("cleaning"),
    BABY("baby"),
    FOOD("food"),

    /**
     * Default for anything we haven't explicitly classified yet.
     * This is the safe fallback.
     */
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
        if (raw == null || raw.isBlank()) {
            return UNKNOWN;
        }
        String normalized = raw.trim().toLowerCase();
        for (ProductDomain d : values()) {
            if (d.code.equals(normalized)) {
                return d;
            }
        }
        return UNKNOWN;
    }

    /** Convenience for checking if this domain is a cleaner. */
    public boolean isCleaning() {
        return this == CLEANING;
    }

    /** Is this something we *might* rate in the app at all (now or later). */
    public boolean isRateableAnyDomain() {
        return this == CLEANING || this == BABY || this == FOOD;
    }
}
