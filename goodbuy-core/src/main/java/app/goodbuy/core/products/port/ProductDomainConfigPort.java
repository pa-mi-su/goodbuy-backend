package app.goodbuy.core.products.port;

import java.util.List;

/**
 * DB-driven domain enable/rate flags.
 *
 * Domain "code" is the stable, persisted lowercase string:
 * e.g. "vitamins", "cleaning", "unknown".
 */
public interface ProductDomainConfigPort {

    /** True if the domain is enabled (supported) in product_domain_config. */
    boolean isEnabled(String domainCode);

    /** True if the domain is rated in product_domain_config. */
    boolean isRated(String domainCode);

    /**
     * List all enabled domain codes (lowercase) from product_domain_config.
     * Example: ["vitamins"]
     * Example: ["cleaning", "vitamins"]
     */
    List<String> enabledDomains();

    /**
     * List all rated domain codes (lowercase) from product_domain_config.
     * Usually a subset of enabledDomains().
     */
    List<String> ratedDomains();
}
