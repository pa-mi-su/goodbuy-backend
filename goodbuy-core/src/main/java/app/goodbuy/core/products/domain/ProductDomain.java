package app.goodbuy.core.products.domain;

/**
 * High-level domains GoodBuy cares about.
 *
 * CLEANING is the only "supported" domain for MVP.
 * Others are here so we can progressively turn them on later.
 *
 * We keep a stable string code for persistence / JSON:
 *   - CLEANING  -> "cleaning"
 *   - BABY      -> "baby"
 *   - FOOD      -> "food"
 *   - OTHER     -> "other"
 *   - UNKNOWN   -> "unknown"
 */
public enum ProductDomain {

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

        String normalized = raw.trim().toLowerCase();
        for (ProductDomain d : values()) {
            if (d.code.equals(normalized)) return d;
        }
        return UNKNOWN;
    }

    public boolean isCleaning() {
        return this == CLEANING;
    }

    public boolean isRateableAnyDomain() {
        return this == CLEANING || this == BABY || this == FOOD;
    }
}
