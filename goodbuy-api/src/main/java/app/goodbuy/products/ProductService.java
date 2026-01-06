package app.goodbuy.products;

import app.goodbuy.adapters.catalog.CatalogTransportException;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import app.goodbuy.core.products.port.ProductLookupPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    /** Optional external catalog client (EAN-DB, EAN-Search, etc.). */
    private final ExternalCatalogClient external;        // may be null

    /** GoodBuy DB lookup (products + product_ingredients + ingredients). */
    private final ProductLookupPort lookup;              // may be null

    /** Snapshot writer into GoodBuy DB (products + product_ingredients). */
    private final ProductSnapshotPort snapshot;          // may be null

    public ProductService(Optional<ExternalCatalogClient> external,
                          Optional<ProductLookupPort> lookup,
                          Optional<ProductSnapshotPort> snapshot) {
        this.external = external.orElse(null);
        this.lookup = lookup.orElse(null);
        this.snapshot = snapshot.orElse(null);

        log.info("ProductService wiring: external={}, lookup={}, snapshot={}",
                this.external != null ? this.external.getClass().getSimpleName() : "<none>",
                this.lookup   != null ? this.lookup.getClass().getSimpleName()   : "<none>",
                this.snapshot != null ? this.snapshot.getClass().getSimpleName() : "<none>");
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
     * DB-first lookup.
     *
     * Returns null only for a TRUE miss (DB miss + external 404/empty).
     *
     * Throws 503 when external catalog is unavailable and DB has no snapshot.
     */
    public ProductDetailDto getByGtinOrNull(String gtin14) {
        String code = normalize(gtin14);
        if (code == null) {
            return null;
        }

        // 1) DB FIRST
        ProductDetailDto fromDbFirst = tryDbLookup(code);
        if (fromDbFirst != null) {
            int count = fromDbFirst.ingredients() == null ? 0 : fromDbFirst.ingredients().size();
            log.info("ProductService.getByGtinOrNull: using DB snapshot with {} ingredients for gtin={}",
                    count, code);
            return fromDbFirst;
        }

        // 2) External if DB has nothing
        ProductDetailDto fromExternal = fetchFromExternal(code); // may throw 503
        if (fromExternal == null) {
            // True miss: no DB + external returned empty (e.g. real 404)
            return null;
        }

        // 3) Snapshot external into GoodBuy DB
        trySnapshotSave(fromExternal);

        // 4) Re-read from DB
        ProductDetailDto fromDbAfterSave = tryDbLookup(code);
        if (fromDbAfterSave != null) {
            int count = fromDbAfterSave.ingredients() == null ? 0 : fromDbAfterSave.ingredients().size();
            log.info("ProductService.getByGtinOrNull: after snapshot, DB has {} ingredients for gtin={}",
                    count, code);
            return fromDbAfterSave;
        }

        // 5) Last resort: return external DTO
        log.warn("ProductService.getByGtinOrNull: snapshot saved but DB still empty for gtin={}, returning external DTO",
                code);
        return fromExternal;
    }

    public ProductDetailDto getDetailByGtinOrNull(String gtin14) {
        return getByGtinOrNull(gtin14);
    }

    // ── internal helpers ───────────────────────────────────────────────────────

    private ProductDetailDto tryDbLookup(String code) {
        if (lookup == null) return null;

        try {
            Optional<ProductDetailDto> opt = lookup.findByGtin(code);
            if (opt.isPresent()) {
                ProductDetailDto dto = opt.get();
                int ingCount = dto.ingredients() == null ? 0 : dto.ingredients().size();
                log.info("goodbuy-db hit gtin14={} name={} brand={} ingredientsCount={}",
                        code, safe(dto.name()), safe(dto.brand()), ingCount);
                return dto;
            }
            log.debug("goodbuy-db miss gtin14={}", code);
            return null;
        } catch (Exception e) {
            log.warn("goodbuy-db error on get gtin14={} msg={}", code, e.getMessage());
            return null;
        }
    }

    private void trySnapshotSave(ProductDetailDto dto) {
        if (snapshot == null || dto == null) return;

        String gtin = dto.gtin();
        if (gtin == null || gtin.isBlank()) return;

        try {
            snapshot.saveSnapshot(dto);
            log.info("goodbuy-db snapshot saved gtin14={} name={} brand={}",
                    gtin, safe(dto.name()), safe(dto.brand()));
        } catch (Exception e) {
            log.warn("goodbuy-db error on snapshot gtin14={} msg={}", gtin, e.getMessage());
        }
    }

    /**
     * External fetch that distinguishes:
     *  - Optional.empty() => true miss
     *  - CatalogTransportException => catalog down => 503
     */
    private ProductDetailDto fetchFromExternal(String code) {
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
            }

            log.warn("catalog miss provider={} gtin14={} durMs={}", provider, code, ms);
            return null;

        } catch (CatalogTransportException e) {
            long ms = Duration.between(t0, Instant.now()).toMillis();
            log.warn("catalog unavailable provider={} gtin14={} durMs={} msg={}",
                    provider, code, ms, e.getMessage());

            // Critical behavior change:
            // DO NOT convert this to "not found". This is a provider outage / throttle / timeout.
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "External catalog temporarily unavailable (" + provider + "). Please retry."
            );

        } catch (Exception e) {
            long ms = Duration.between(t0, Instant.now()).toMillis();
            log.warn("catalog unexpected_error provider={} gtin14={} durMs={} type={} msg={}",
                    provider, code, ms, e.getClass().getSimpleName(), e.getMessage());

            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "External catalog temporarily unavailable (" + provider + "). Please retry."
            );
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
