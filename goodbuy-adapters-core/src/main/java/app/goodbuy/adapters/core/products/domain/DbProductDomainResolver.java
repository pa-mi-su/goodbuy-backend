package app.goodbuy.adapters.core.products.domain;

import app.goodbuy.adapters.core.products.model.ProductDomainMappingEntity;
import app.goodbuy.adapters.core.products.model.ProductDomainMappingEntity.MatchField;
import app.goodbuy.adapters.core.products.model.ProductDomainMappingEntity.MatchType;
import app.goodbuy.adapters.core.products.repo.ProductDomainMappingRepository;
import app.goodbuy.core.products.domain.ProductDomain;
import app.goodbuy.core.products.port.ProductDomainResolverPort;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;

/**
 * DB-backed product domain resolver for GoodBuy.
 *
 * Responsibilities:
 *   - Take free-text from upstream (category / title / brand)
 *   - Map it into one of a small, stable domains:
 *       vitamins, cleaning, baby, food, other, unknown
 *
 * Rules live in the product_domain_mapping table.
 *
 * Matching:
 *   - We normalize fields and patterns to lowercase.
 *   - For each ACTIVE rule (ordered by priority ascending):
 *       1) pick the right field(s) based on matchField
 *       2) apply matchType (EQUALS or CONTAINS)
 *       3) first rule that matches → we return its domain
 *
 * IMPORTANT:
 *   - No hardcoded "soap", "cleaner", etc. here.
 *   - To change behavior, edit rows in product_domain_mapping, not this class.
 */
@Component
public final class DbProductDomainResolver implements ProductDomainResolverPort {

    private final ProductDomainMappingRepository mappingRepo;

    public DbProductDomainResolver(ProductDomainMappingRepository mappingRepo) {
        this.mappingRepo = mappingRepo;
    }

    @Override
    public ProductDomain classify(String existingDomain,
                                  String category,
                                  String title,
                                  String brand) {

        // 1) If caller already has a structured domain, respect it (when valid).
        ProductDomain fromExisting = parseExisting(existingDomain);
        if (fromExisting != null && fromExisting != ProductDomain.UNKNOWN) {
            return fromExisting;
        }

        // 2) Normalize input fields
        String normCategory = normalize(category);
        String normTitle    = normalize(title);
        String normBrand    = normalize(brand);

        if (normCategory.isEmpty() && normTitle.isEmpty() && normBrand.isEmpty()) {
            return ProductDomain.UNKNOWN;
        }

        // 3) Apply DB-driven mapping rules in priority order
        List<ProductDomainMappingEntity> rules =
                mappingRepo.findByActiveTrueOrderByPriorityAsc();

        for (ProductDomainMappingEntity rule : rules) {
            String pattern = normalize(rule.getPattern());
            if (pattern.isEmpty()) {
                continue;
            }

            MatchField field = rule.getMatchField();
            if (field == null) {
                field = MatchField.ALL;
            }

            String targetText = switch (field) {
                case CATEGORY -> normCategory;
                case TITLE    -> normTitle;
                case BRAND    -> normBrand;
                case ALL -> {
                    StringBuilder sb = new StringBuilder();
                    if (!normTitle.isEmpty()) {
                        sb.append(normTitle).append(' ');
                    }
                    if (!normCategory.isEmpty()) {
                        sb.append(normCategory).append(' ');
                    }
                    if (!normBrand.isEmpty()) {
                        sb.append(normBrand);
                    }
                    yield sb.toString().trim();
                }
            };

            if (targetText.isEmpty()) {
                continue;
            }

            MatchType type = rule.getMatchType();
            if (type == null) {
                type = MatchType.CONTAINS;
            }

            boolean match = switch (type) {
                case EQUALS   -> targetText.equals(pattern);
                case CONTAINS -> targetText.contains(pattern);
            };

            if (!match) {
                continue;
            }

            // 4) Rule matched: parse its canonical lowercase domain code into our enum
            ProductDomain domain = parseExisting(rule.getDomain());
            if (domain != null) {
                return domain;
            }
        }

        // 5) Nothing matched → UNKNOWN (we don't guess)
        return ProductDomain.UNKNOWN;
    }

    // ─────────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────────

    private ProductDomain parseExisting(String existingDomain) {
        if (existingDomain == null || existingDomain.isBlank()) {
            return null;
        }
        ProductDomain parsed = ProductDomain.fromCode(existingDomain);
        return parsed == ProductDomain.UNKNOWN ? ProductDomain.UNKNOWN : parsed;
    }

    private String normalize(String s) {
        return (s == null) ? "" : s.trim().toLowerCase(Locale.ROOT);
    }
}
