package app.goodbuy.core.products.domain;

import org.springframework.stereotype.Component;

import java.util.Locale;

/**
 * Canonical v1 product domain classifier for GoodBuy.
 *
 * Responsibility:
 *   - Take free-text from upstream (category / title / brand)
 *   - Map it into one of a small, stable domains:
 *       CLEANING, BABY, FOOD, OTHER, UNKNOWN
 *
 * This lives in core so that:
 *   - Adapters (EAN-DB, EAN-Search, DB snapshot) DO NOT invent their own rules
 *   - Controllers / services can just ask "what domain is this product?"
 *
 * The rules in here are our current product-domain SPEC.
 * If we ever move to a DB table or ML classifier, callers do not change.
 */
@Component
public final class ProductDomainClassifier {

    /**
     * High-level domains we care about.
     *
     * CLEANING is the only "supported" domain for MVP.
     * Others are here so we can progressively turn them on later.
     */
    public enum Domain {
        CLEANING,
        BABY,
        FOOD,
        OTHER,
        UNKNOWN
    }

    /**
     * Classify a product into a high-level domain.
     *
     * @param existingDomain domain string already known (e.g. from DB or upstream),
     *                       may be null / "unknown" / arbitrary text.
     * @param category       free-text category from provider (e.g. "Household cleaners")
     * @param title          product title / name
     * @param brand          brand / manufacturer name
     *
     * @return a stable {@link Domain} enum value.
     */
    public Domain classify(String existingDomain,
                           String category,
                           String title,
                           String brand) {

        // 1) If caller already has a structured domain, respect it (when valid).
        Domain fromExisting = parseExisting(existingDomain);
        if (fromExisting != null && fromExisting != Domain.UNKNOWN) {
            return fromExisting;
        }

        // 2) Build a normalized text blob from known fields
        StringBuilder sb = new StringBuilder();
        if (title != null) {
            sb.append(title).append(' ');
        }
        if (category != null) {
            sb.append(category).append(' ');
        }
        if (brand != null) {
            sb.append(brand);
        }

        String text = sb.toString().trim().toLowerCase(Locale.ROOT);
        if (text.isEmpty()) {
            return Domain.UNKNOWN;
        }

        // 3) CLEANING — the only supported domain for MVP
        if (looksLikeCleaning(text)) {
            return Domain.CLEANING;
        }

        // 4) Future switches:
        // if (looksLikeBaby(text)) return Domain.BABY;
        // if (looksLikeFood(text)) return Domain.FOOD;

        // 5) Everything else for now is outside our rating scope
        return Domain.UNKNOWN;
    }

    // ─────────────────────────────────────────────────────────────────────
    // Internal helpers
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Normalize an existing domain string (e.g. "cleaning", "BABY") into our enum.
     * Returns null if we can't interpret it.
     */
    private Domain parseExisting(String existingDomain) {
        if (existingDomain == null || existingDomain.isBlank()) {
            return null;
        }
        String normalized = existingDomain.trim().toUpperCase(Locale.ROOT);
        try {
            return Domain.valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            // Unknown / legacy domain string → let classifier decide from text
            return null;
        }
    }

    /**
     * v1 definition of "cleaning" products.
     *
     * This is the ONLY place we do text-based checks. Everywhere else
     * just deals with the Domain enum.
     *
     * We intentionally keep this small and conservative: products that
     * match these rules are clearly household / laundry cleaners.
     */
    private boolean looksLikeCleaning(String text) {
        // general cleaners
        if (text.contains("cleaner")) return true;          // "All Purpose Cleaner", "Glass Cleaner"
        if (text.contains("cleaning")) return true;         // "Cleaning vinegar"

        //soap
        if (text.contains("soap")) return true;

        // laundry
        if (text.contains("laundry detergent")) return true;
        if (text.contains("laundry soap")) return true;
        if (text.contains("fabric softener")) return true;
        if (text.contains("stain remover")) return true;

        // disinfectants / bleaches
        if (text.contains("disinfectant")) return true;
        if (text.contains("bleach")) return true;

        // surfaces / bathrooms
        if (text.contains("surface cleaner")) return true;
        if (text.contains("bathroom cleaner")) return true;
        if (text.contains("toilet bowl")) return true;
        if (text.contains("glass cleaner")) return true;
        if (text.contains("all purpose") && text.contains("clean")) return true;

        // degreasers / descalers
        if (text.contains("degreaser")) return true;
        if (text.contains("descaler")) return true;

        // If it doesn't clearly look like a cleaner, we don't guess.
        return false;
    }
}
