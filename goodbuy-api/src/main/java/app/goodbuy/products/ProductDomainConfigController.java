package app.goodbuy.products;

import app.goodbuy.adapters.core.products.model.ProductDomainConfigEntity;
import app.goodbuy.adapters.core.products.repo.ProductDomainConfigRepository;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Domain configuration endpoint.
 *
 * DB (product_domain_config) is the single source of truth for:
 *  - which domains are enabled (supported)
 *  - which domains are rated
 *
 * iOS/UI should consume this endpoint for "We focus on ..." copy.
 */
@RestController
@RequestMapping(value = "/v1/product-domains", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductDomainConfigController {

    private final ProductDomainConfigRepository repo;

    public ProductDomainConfigController(ProductDomainConfigRepository repo) {
        this.repo = repo;
    }

    @GetMapping
    public ResponseEntity<?> list() {
        List<ProductDomainConfigEntity> all = repo.findAll();

        // Stable ordering for UI (alphabetical, case-insensitive)
        all.sort(Comparator.comparing(
                e -> safeLower(e.getDomain()),
                Comparator.nullsLast(String::compareTo)
        ));

        List<Map<String, Object>> domains = all.stream()
                .map(e -> Map.<String, Object>of(
                        "domain", e.getDomain(),
                        "enabled", e.isEnabled(),
                        "rated", e.isRated()
                ))
                .collect(Collectors.toList());

        List<String> enabledDomains = all.stream()
                .filter(ProductDomainConfigEntity::isEnabled)
                .map(ProductDomainConfigEntity::getDomain)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList());

        List<String> ratedDomains = all.stream()
                .filter(ProductDomainConfigEntity::isRated)
                .map(ProductDomainConfigEntity::getDomain)
                .filter(s -> s != null && !s.isBlank())
                .collect(Collectors.toList());

        Map<String, Object> body = Map.of(
                "generatedAt", Instant.now().toString(),
                "domains", domains,
                "enabledDomains", enabledDomains,
                "ratedDomains", ratedDomains
        );

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .body(body);
    }

    private static String safeLower(String s) {
        if (s == null) return null;
        String t = s.trim();
        if (t.isEmpty()) return null;
        return t.toLowerCase(Locale.ROOT);
    }
}
