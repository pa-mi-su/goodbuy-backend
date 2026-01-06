package app.goodbuy.core.products.dto;

import java.util.List;

/**
 * DB-backed domain configuration response for the client.
 *
 * enabledDomains: domains currently enabled in product_domain_config
 * ratedDomains: domains currently rated in product_domain_config
 * focusLabel: human-friendly label for UI (e.g. "Vitamins" or "Cleaning and Vitamins")
 */
public record ProductDomainsResponse(
        List<String> enabledDomains,
        List<String> ratedDomains,
        String focusLabel
) {}
