package app.goodbuy.products;

import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import app.goodbuy.core.products.port.ProductLookupPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import app.goodbuy.core.products.util.IngredientMerger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
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

        if (this.external != null) {
            log.info("catalog client wired: {}", this.external.getClass().getName());
        } else {
            log.info("no external catalog client configured; running without external catalog");
        }

        if (this.lookup != null) {
            log.info("product lookup wired: {}", this.lookup.getClass().getName());
        } else {
            log.info("no GoodBuy product lookup configured; will skip DB enrichment/fallback");
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
     * FINAL long-term lookup flow:
     *
     * 1) Normalize GTIN.
     * 2) Call external catalog FIRST (EAN-DB) → authoritative ingredient list.
     * 3) If external hit:
     *      - snapshot into GoodBuy DB (products + product_ingredients).
     *      - re-read from GoodBuy DB and merge ingredients:
     *          • DB ingredient rows (with ratings) override external ones
     *          • External fills gaps where we have no DB ingredient yet
     *      - prefer DB images (S3) if present.
     * 4) If external miss/error → fallback to GoodBuy DB snapshot only.
     */
    public ProductDetailDto getByGtinOrNull(String gtin14) {
        String code = normalize(gtin14);
        if (code == null) {
            return null;
        }

        // 1) External FIRST (authoritative ingredient list)
        ProductDetailDto fromExternal = fetchFromExternalOrNull(code);
        if (fromExternal != null) {
            // 2) Snapshot into GoodBuy DB (best-effort)
            trySnapshotSave(fromExternal);

            // 3) Enrich using GoodBuy DB ingredient catalog if possible
            ProductDetailDto enriched = tryEnrichWithGoodBuy(fromExternal);
            return enriched;
        }

        // 4) Fallback: GoodBuy DB snapshot only (if external is down / missing)
        return tryDbLookup(code);
    }

    /** Rich detail lookup: same flow as simple lookup. */
    public ProductDetailDto getDetailByGtinOrNull(String gtin14) {
        return getByGtinOrNull(gtin14);
    }

    // ── internal helpers ───────────────────────────────────────────────────────

    /**
     * Re-read snapshot from GoodBuy DB and merge its ingredients/images
     * into the external DTO.
     */
    private ProductDetailDto tryEnrichWithGoodBuy(ProductDetailDto externalDto) {
        if (externalDto == null || lookup == null) {
            return externalDto;
        }
        String gtin = externalDto.gtin();
        if (gtin == null || gtin.isBlank()) return externalDto;

        try {
            Optional<ProductDetailDto> optDb = lookup.findByGtin(gtin);
            if (optDb.isEmpty()) {
                log.debug("ingredient enrichment: no GoodBuy DB snapshot yet for gtin14={}", gtin);
                return externalDto;
            }

            ProductDetailDto dbDto = optDb.get();

            List<ProductDetailDto.IngredientDto> dbIngredients = dbDto.ingredients();
            List<ProductDetailDto.IngredientDto> externalIngredients = externalDto.ingredients();

            if (externalIngredients == null || externalIngredients.isEmpty()) {
                // Nothing to merge; DB may still have curated ingredients if you populated them manually.
                if (dbIngredients != null && !dbIngredients.isEmpty()) {
                    log.info("ingredient enrichment: external had 0 ingredients, using DB-only for gtin14={}", gtin);
                    return dbDto;
                }
                return externalDto;
            }

            if (dbIngredients == null || dbIngredients.isEmpty()) {
                // We don’t know any GoodBuy ingredients yet → just return external.
                log.debug("ingredient enrichment: DB has 0 ingredients; keeping external only for gtin14={}", gtin);
                return externalDto;
            }

            // Merge: GoodBuy ingredients override external ones by canonical key
            List<ProductDetailDto.IngredientDto> mergedIngredients =
                    IngredientMerger.merge(dbIngredients, externalIngredients);

            // Prefer DB images (will be S3 URLs) if present; otherwise keep external images
            List<ProductDetailDto.ImageDto> images =
                    (dbDto.images() != null && !dbDto.images().isEmpty())
                            ? dbDto.images()
                            : externalDto.images();

            // NEW: carry domain through, preferring DB domain when present
            String domain = (dbDto.domain() != null && !dbDto.domain().isBlank())
                    ? dbDto.domain()
                    : externalDto.domain();

            ProductDetailDto mergedDto = new ProductDetailDto(
                    // Product identity/label: keep external as the source of truth for text
                    externalDto.gtin(),
                    externalDto.name(),
                    externalDto.brand(),
                    externalDto.category(),
                    externalDto.description(),
                    images,
                    mergedIngredients,
                    externalDto.titles(),
                    externalDto.manufacturer(),
                    "GOODBUY-DB+EAN-DB",
                    domain
            );

            log.info("ingredient enrichment: applied GoodBuy overrides for gtin14={} (dbIngs={} externalIngs={} merged={})",
                    gtin,
                    dbIngredients.size(),
                    externalIngredients.size(),
                    mergedIngredients.size());

            return mergedDto;
        } catch (Exception e) {
            log.warn("ingredient enrichment failed gtin14={} type={} msg={}",
                    externalDto.gtin(), e.getClass().getSimpleName(), e.getMessage());
            // Fall back to raw external DTO if enrichment fails
            return externalDto;
        }
    }

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
