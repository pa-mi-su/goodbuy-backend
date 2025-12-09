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
     * New lookup flow (DB-first):
     *
     * 1) Normalize GTIN.
     * 2) Try GoodBuy DB snapshot FIRST (products + product_ingredients + ingredients).
     *    - If found, return it immediately.
     * 3) If DB misses:
     *    - Call external catalog (EAN-DB) to fetch full label + images.
     *    - Snapshot into GoodBuy DB (products + product_ingredients, and product scores).
     *    - Re-read from GoodBuy DB and return that if present.
     *    - If still missing, fall back to the external DTO.
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

        // 2) External (EAN-DB) if DB has nothing
        ProductDetailDto fromExternal = fetchFromExternalOrNull(code);
        if (fromExternal == null) {
            // No external, no DB → nothing
            return null;
        }

        // 3) Snapshot external into GoodBuy DB
        trySnapshotSave(fromExternal);

        // 4) Re-read from DB (now with product_ingredients links & scores)
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

    /** Detail = same behavior as simple lookup for now. */
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
                int ingCount = dto.ingredients() == null ? 0 : dto.ingredients().size();
                log.info("goodbuy-db hit gtin14={} name={} brand={} ingredientsCount={}",
                        code, safe(dto.name()), safe(dto.brand()), ingCount);
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
            log.info("goodbuy-db snapshot saved gtin14={} name={} brand={}",
                    gtin, safe(dto.name()), safe(dto.brand()));
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
