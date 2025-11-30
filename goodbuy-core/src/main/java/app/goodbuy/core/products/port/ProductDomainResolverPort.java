package app.goodbuy.core.products.port;

import app.goodbuy.core.products.domain.ProductDomain;

/**
 * Core-side port for resolving a product's high-level domain
 * (CLEANING, BABY, FOOD, OTHER, UNKNOWN).
 *
 * Implementations live in the domain / adapter layer, e.g.:
 *   - ProductDomainClassifier (current text-based rules)
 *   - Future DB/ML-based resolvers that can wrap/augment the rules.
 */
public interface ProductDomainResolverPort {

    /**
     * Classify a product into a high-level domain.
     *
     * @param existingDomain domain string already known (e.g. from DB or upstream),
     *                       may be null / "unknown" / arbitrary text.
     * @param category       free-text category from provider (e.g. "Household cleaners")
     * @param title          product title / name
     * @param brand          brand / manufacturer name
     *
     * @return a stable {@link ProductDomain} enum value.
     */
    ProductDomain classify(
            String existingDomain,
            String category,
            String title,
            String brand
    );
}
