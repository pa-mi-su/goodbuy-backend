package app.goodbuy.products;

import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /**
     * Optional external catalog client (EAN-DB, EAN-Search, etc.).
     * Provided via the core port ExternalCatalogClient.
     */
    private final ExternalCatalogClient external; // may be null

    public ProductService(Optional<ExternalCatalogClient> external) {
        this.external = external.orElse(null);

        if (this.external != null) {
            log.info("catalog client wired: {}", this.external.getClass().getName());
        } else {
            log.info("no external catalog client configured; running without external catalog");
        }
    }

    /** Human-friendly provider name for logs & responses. */
    public String activeSourceName() {
        if (external == null) {
            return "internal";
        }
        String n = external.getClass().getSimpleName().toLowerCase();
        if (n.contains("eandb")) return "EAN-DB";
        if (n.contains("eansearch")) return "EAN-Search";
        return external.getClass().getSimpleName();
    }

    /**
     * Simple lookup by GTIN-14.
     * Uses ExternalCatalogClient.findByGtin(...).
     * Returns null on:
     *  - no configured client
     *  - not found
     *  - unexpected runtime error (defensive)
     */
    public ProductDetailDto getByGtinOrNull(String gtin14) {
        String code = normalize(gtin14);
        if (code == null) return null;
        if (external == null) return null;

        String provider = activeSourceName();
        Instant t0 = Instant.now();

        try {
            Optional<ProductDetailDto> opt = external.findByGtin(code);
            long ms = Duration.between(t0, Instant.now()).toMillis();

            if (opt.isPresent()) {
                ProductDetailDto dto = opt.get();
                log.info("catalog hit provider={} gtin14={} name={} brand={} durMs={}",
                        provider, code, safe(dto.name()), safe(dto.brand()), ms);
                return dto;
            } else {
                log.warn("catalog miss provider={} gtin14={} durMs={}", provider, code, ms);
                return null;
            }
        } catch (Exception e) {
            long ms = Duration.between(t0, Instant.now()).toMillis();
            log.warn("catalog unexpected_error provider={} gtin14={} durMs={} type={} msg={}",
                    provider, code, ms, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    /**
     * Rich detail lookup.
     * For now, same as getByGtinOrNull(...) because ExternalCatalogClient
     * already returns the rich ProductDetailDto shape.
     * Kept separate so we can evolve behavior later without breaking callers.
     */
    public ProductDetailDto getDetailByGtinOrNull(String gtin14) {
        return getByGtinOrNull(gtin14);
    }

    // ── helpers ────────────────────────────────────────────────────────────────

    private static String normalize(String gtin14) {
        if (gtin14 == null) return null;
        String s = gtin14.trim();
        return s.isEmpty() ? null : s;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
