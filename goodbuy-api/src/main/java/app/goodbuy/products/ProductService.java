package app.goodbuy.products;

import app.goodbuy.adapters.catalog.CatalogTransportException;
import app.goodbuy.adapters.catalog.ExternalCatalogClient;
import app.goodbuy.adapters.catalog.eandb.EanDbCatalogClient;
import app.goodbuy.core.products.dto.ProductDetailDto;
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
     * Present only when goodbuy.catalog.enabled=true and a provider bean is configured.
     */
    private final Optional<ExternalCatalogClient> external;

    public ProductService(Optional<ExternalCatalogClient> external) {
        this.external = external;
        external.ifPresent(c -> log.info("catalog client wired: {}", c.getClass().getName()));
    }

    /** Expose a friendly provider name for responses & logs. */
    public String activeSourceName() {
        return external
                .map(c -> {
                    String n = c.getClass().getSimpleName().toLowerCase();
                    if (n.contains("eandb")) return "EAN-DB";
                    if (n.contains("eansearch")) return "EAN-Search";
                    return c.getClass().getSimpleName();
                })
                .orElse("internal");
    }

    /**
     * Simple lookup by GTIN-14.
     * Uses the configured ExternalCatalogClient.findByGtin(...) which now returns ProductDetailDto.
     * Returns null when not found or on transport error.
     */
    public ProductDetailDto getByGtinOrNull(String gtin14) {
        String code = gtin14 == null ? null : gtin14.trim();
        if (code == null || code.isEmpty()) return null;
        return fetchFromExternalOrNull(code);
    }

    /**
     * Rich detail lookup for iOS detail screen.
     * - If EanDbCatalogClient is wired, uses its findDetailByGtin(...) for full data.
     * - Otherwise falls back to ExternalCatalogClient.findByGtin(...).
     * Returns null when not found or on transport error.
     */
    public ProductDetailDto getDetailByGtinOrNull(String gtin14) {
        String code = gtin14 == null ? null : gtin14.trim();
        if (code == null || code.isEmpty()) return null;

        ExternalCatalogClient client = external.orElse(null);
        if (client == null) return null;

        String provider = activeSourceName();
        Instant t0 = Instant.now();

        try {
            // Prefer rich method when using EAN-DB
            if (client instanceof EanDbCatalogClient eandb) {
                Optional<ProductDetailDto> opt = eandb.findByGtin(code);
                long ms = Duration.between(t0, Instant.now()).toMillis();

                if (opt.isPresent()) {
                    ProductDetailDto dto = opt.get();
                    log.info("catalog detail hit provider={} gtin14={} name={} brand={} durMs={}",
                            provider, code, safe(dto.name()), safe(dto.brand()), ms);
                    return dto;
                } else {
                    log.warn("catalog detail miss provider={} gtin14={} durMs={}", provider, code, ms);
                    return null;
                }
            }

            // Generic clients: rely on their findByGtin already returning ProductDetailDto
            Optional<ProductDetailDto> opt = client.findByGtin(code);
            long ms = Duration.between(t0, Instant.now()).toMillis();

            if (opt.isPresent()) {
                ProductDetailDto dto = opt.get();
                log.info("catalog detail(generic) hit provider={} gtin14={} name={} brand={} durMs={}",
                        provider, code, safe(dto.name()), safe(dto.brand()), ms);
                return dto;
            } else {
                log.warn("catalog detail(generic) miss provider={} gtin14={} durMs={}", provider, code, ms);
                return null;
            }

        } catch (CatalogTransportException e) {
            long ms = Duration.between(t0, Instant.now()).toMillis();
            log.warn("catalog detail error provider={} gtin14={} durMs={} msg={}",
                    provider, code, ms, e.getMessage());
            return null;
        } catch (Exception e) {
            long ms = Duration.between(t0, Instant.now()).toMillis();
            log.warn("catalog detail unexpected_error provider={} gtin14={} durMs={} type={} msg={}",
                    provider, code, ms, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    // ── internal helper ────────────────────────────────────────────────────────

    private ProductDetailDto fetchFromExternalOrNull(String gtin14) {
        ExternalCatalogClient client = external.orElse(null);
        if (client == null) return null;

        String provider = activeSourceName();
        Instant t0 = Instant.now();

        try {
            Optional<ProductDetailDto> found = client.findByGtin(gtin14);
            long ms = Duration.between(t0, Instant.now()).toMillis();

            if (found.isPresent()) {
                ProductDetailDto dto = found.get();
                log.info("catalog hit provider={} gtin14={} name={} brand={} durMs={}",
                        provider, gtin14, safe(dto.name()), safe(dto.brand()), ms);
                return dto;
            } else {
                log.warn("catalog miss provider={} gtin14={} durMs={}", provider, gtin14, ms);
                return null;
            }

        } catch (CatalogTransportException e) {
            long ms = Duration.between(t0, Instant.now()).toMillis();
            log.warn("catalog error provider={} gtin14={} durMs={} msg={}",
                    provider, gtin14, ms, e.getMessage());
            return null;
        } catch (Exception e) {
            long ms = Duration.between(t0, Instant.now()).toMillis();
            log.warn("catalog unexpected_error provider={} gtin14={} durMs={} type={} msg={}",
                    provider, gtin14, ms, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
