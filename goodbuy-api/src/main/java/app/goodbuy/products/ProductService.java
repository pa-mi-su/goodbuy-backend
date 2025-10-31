package app.goodbuy.products;

import app.goodbuy.catalog.CatalogTransportException;
import app.goodbuy.catalog.ExternalCatalogClient;
import app.goodbuy.catalog.eandb.EanDbCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /** Present only when goodbuy.catalog.enabled=true and a provider is configured. */
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

    /** Look up a product by GTIN-14. Returns null when not found. (simple view) */
    public ProductDto getByGtinOrNull(String gtin14) {
        final String code = (gtin14 == null) ? null : gtin14.trim();
        if (code == null || code.isEmpty()) return null;

        if (external.isPresent()) {
            return fetchSimpleFromExternalOrNull(code);
        }
        return null;
    }

    /** NEW: rich detail for iOS detail screen. Returns null when not found. */
    public ProductDetailDto getDetailByGtinOrNull(String gtin14) {
        final String code = (gtin14 == null) ? null : gtin14.trim();
        if (code == null || code.isEmpty()) return null;

        final ExternalCatalogClient client = external.orElse(null);
        final String provider = activeSourceName();
        final Instant t0 = Instant.now();

        if (client == null) return null;

        try {
            // If the wired client is EAN-DB, use its rich method.
            if (client instanceof EanDbCatalogClient eandb) {
                var opt = eandb.findDetailByGtin(code);
                long ms = Duration.between(t0, Instant.now()).toMillis();
                if (opt.isPresent()) {
                    var dto = opt.get();
                    log.info("catalog detail hit provider={} gtin14={} name={} brand={} durMs={}",
                            provider, code, safe(dto.name()), safe(dto.brand()), ms);
                    return dto;
                } else {
                    log.warn("catalog detail miss provider={} gtin14={} durMs={}", provider, code, ms);
                    return null;
                }
            }

            // Fallback: if some other client is wired, map the simple ProductDto → ProductDetailDto.
            var simpleOpt = client.findByGtin(code);
            long ms = Duration.between(t0, Instant.now()).toMillis();
            if (simpleOpt.isEmpty()) {
                log.warn("catalog detail miss (simple-fallback) provider={} gtin14={} durMs={}", provider, code, ms);
                return null;
            }
            var s = simpleOpt.get();
            var images = new ArrayList<ProductDetailDto.ImageDto>();
            for (String url : s.images()) images.add(new ProductDetailDto.ImageDto(url, null, null));

            var ingredients = new ArrayList<ProductDetailDto.IngredientDto>();
            for (String name : s.ingredients()) {
                ingredients.add(new ProductDetailDto.IngredientDto(
                        null,              // id
                        name,              // original
                        null,              // canonical
                        null,              // externalIds
                        null,              // isVegan
                        null               // isVegetarian
                ));
            }

            var out = new ProductDetailDto(
                    s.gtin(),
                    s.name(),
                    s.brand(),
                    s.category(),
                    null,                 // description (unknown in simple)
                    images,
                    ingredients,
                    null,                 // titles
                    null,                 // manufacturer
                    provider
            );
            log.info("catalog detail synthesized provider={} gtin14={} durMs={}", provider, code, ms);
            return out;

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

    // ── internal helpers ─────────────────────────────────────────────────────────

    private ProductDto fetchSimpleFromExternalOrNull(String gtin14) {
        final ExternalCatalogClient client = external.orElse(null);
        if (client == null) return null;

        final String provider = activeSourceName();
        final Instant t0 = Instant.now();
        try {
            var found = client.findByGtin(gtin14);
            long ms = Duration.between(t0, Instant.now()).toMillis();

            if (found.isPresent()) {
                var dto = found.get();
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

    private static String safe(String s) { return (s == null || s.isBlank()) ? "-" : s; }
}
