package app.goodbuy.products;

import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import app.goodbuy.core.products.port.ProductCachePort;
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
     */
    private final ExternalCatalogClient external; // may be null

    /**
     * Optional DB-backed product cache.
     * Implemented by ProductCacheAdapter in goodbuy-adapters-core.
     */
    private final ProductCachePort cache; // may be null

    public ProductService(Optional<ExternalCatalogClient> external,
                          Optional<ProductCachePort> cache) {
        this.external = external.orElse(null);
        this.cache = cache.orElse(null);

        if (this.external != null) {
            log.info("catalog client wired: {}", this.external.getClass().getName());
        } else {
            log.info("no external catalog client configured; running without external catalog");
        }

        if (this.cache != null) {
            log.info("product cache wired: {}", this.cache.getClass().getName());
        } else {
            log.info("no product cache configured; running cacheless");
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
     * Main lookup flow (used by simple product endpoint).
     *
     * 1) Normalize GTIN.
     * 2) Try database cache.
     * 3) If cache miss → call external.
     * 4) If external hit → persist into cache (best-effort).
     */
    public ProductDetailDto getByGtinOrNull(String gtin14) {
        String code = normalize(gtin14);
        if (code == null) {
            return null;
        }

        // 1) Try cache first
        ProductDetailDto cached = tryCacheHit(code);
        if (cached != null) {
            return cached;
        }

        // 2) Fallback to external
        ProductDetailDto fromExternal = fetchFromExternalOrNull(code);

        // 3) On success, write-through into cache (non-fatal if it fails)
        if (fromExternal != null) {
            tryCacheSave(fromExternal);
        }

        return fromExternal;
    }

    /**
     * Rich detail lookup.
     * For now uses the same flow as getByGtinOrNull, since ProductDetailDto is already rich.
     */
    public ProductDetailDto getDetailByGtinOrNull(String gtin14) {
        return getByGtinOrNull(gtin14);
    }

    // ── internal helpers ───────────────────────────────────────────────────────

    private ProductDetailDto tryCacheHit(String code) {
        if (cache == null) {
            return null;
        }
        try {
            Optional<ProductDetailDto> opt = cache.findByGtin(code);
            if (opt.isPresent()) {
                ProductDetailDto dto = opt.get();
                log.info("product-cache hit gtin14={} source={}", code, safe(dto.source()));
                return dto;
            }
            log.debug("product-cache miss gtin14={}", code);
            return null;
        } catch (Exception e) {
            log.warn("product-cache error on get gtin14={} msg={}", code, e.getMessage());
            return null; // never break request due to cache
        }
    }

    private void tryCacheSave(ProductDetailDto dto) {
        if (cache == null || dto == null) {
            return;
        }
        String gtin = dto.gtin();
        if (gtin == null || gtin.isBlank()) {
            return;
        }

        try {
            cache.save(dto);
        } catch (Exception e) {
            log.warn("product-cache error on save gtin14={} msg={}", gtin, e.getMessage());
        }
    }

    private ProductDetailDto fetchFromExternalOrNull(String code) {
        if (external == null) {
            return null;
        }

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

    private static String normalize(String gtin14) {
        if (gtin14 == null) return null;
        String s = gtin14.trim();
        return s.isEmpty() ? null : s;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
