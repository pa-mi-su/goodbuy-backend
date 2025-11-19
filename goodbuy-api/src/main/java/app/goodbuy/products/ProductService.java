package app.goodbuy.products;

import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import app.goodbuy.core.products.port.ProductLookupPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /** Optional external catalog client (EAN-DB, EAN-Search, etc.). */
    private final ExternalCatalogClient external;        // may be null

    /** Optional GoodBuy DB lookup (products + product_ingredients + ingredients). */
    private final ProductLookupPort lookup;              // may be null

    /** Optional snapshot writer into GoodBuy DB (products + product_ingredients). */
    private final ProductSnapshotPort snapshot;          // may be null

    public ProductService(Optional<ExternalCatalogClient> external,
                          Optional<ProductLookupPort> lookup,
                          Optional<ProductSnapshotPort> snapshot) {
        this.external = external.orElse(null);
        this.lookup = lookup.orElse(null);
        this.snapshot = snapshot.orElse(null);

        if (this.external != null) {
            log.info("catalog client wired: {}", this.external.getClass().getName());
        } else {
            log.info("no external catalog client configured; running without external catalog");
        }

        if (this.lookup != null) {
            log.info("product lookup wired: {}", this.lookup.getClass().getName());
        } else {
            log.info("no GoodBuy product lookup configured; will skip DB-first lookup");
        }

        if (this.snapshot != null) {
            log.info("product snapshot wired: {}", this.snapshot.getClass().getName());
        } else {
            log.info("no product snapshot configured; external hits will NOT be persisted");
        }
    }

    /** Human-friendly provider name for logs & error responses (external side). */
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
     * Main lookup flow (simple product endpoint).
     *
     * 1) Normalize GTIN.
     * 2) Try GoodBuy DB (products + product_ingredients + ingredients).
     * 3) If DB miss → call external.
     * 4) If external hit → persist snapshot into GoodBuy DB (best-effort).
     */
    public ProductDetailDto getByGtinOrNull(String gtin14) {
        String code = normalize(gtin14);
        if (code == null) {
            return null;
        }

        // 1) Try GoodBuy DB first (our own master product + ingredient links)
        ProductDetailDto fromDb = tryDbLookup(code);
        if (fromDb != null) {
            return fromDb;
        }

        // 2) Fallback to external catalog (EAN-DB, etc.)
        ProductDetailDto fromExternal = fetchFromExternalOrNull(code);

        // 3) On success, snapshot into GoodBuy DB (non-fatal if it fails)
        if (fromExternal != null) {
            trySnapshotSave(fromExternal);
        }

        return fromExternal;
    }

    /** Rich detail lookup: for now same flow as simple lookup. */
    public ProductDetailDto getDetailByGtinOrNull(String gtin14) {
        return getByGtinOrNull(gtin14);
    }

    // ── internal helpers ───────────────────────────────────────────────────────

    private ProductDetailDto tryDbLookup(String code) {
        if (lookup == null) {
            return null;
        }
        try {
            Optional<ProductDetailDto> opt = lookup.findByGtin(code);
            if (opt.isPresent()) {
                ProductDetailDto dto = opt.get();
                log.info("goodbuy-db hit gtin14={} name={} brand={}",
                        code, safe(dto.name()), safe(dto.brand()));
                return dto;
            }
            log.debug("goodbuy-db miss gtin14={}", code);
            return null;
        } catch (Exception e) {
            log.warn("goodbuy-db error on get gtin14={} msg={}", code, e.getMessage());
            // Never break the request because our internal lookup failed
            return null;
        }
    }

    private void trySnapshotSave(ProductDetailDto dto) {
        if (snapshot == null || dto == null) {
            return;
        }
        String gtin = dto.gtin();
        if (gtin == null || gtin.isBlank()) {
            return;
        }

        try {
            snapshot.saveSnapshot(dto);
        } catch (Exception e) {
            // Non-fatal: the user still gets the external product; we just failed to persist it.
            log.warn("goodbuy-db error on snapshot gtin14={} msg={}", gtin, e.getMessage());
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
