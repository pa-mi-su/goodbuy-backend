package app.goodbuy.products;

import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import app.goodbuy.adapters.core.products.scoring.ProductScoringAdapterService;
import app.goodbuy.ingredients.IngredientOnDemandResearchService;
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
    private final AsyncProductIngestionService asyncIngestionService;
    private final ProductEvidenceReportService productEvidenceReportService;
    private final IngredientOnDemandResearchService ingredientOnDemandResearchService;
    private final ProductScoringAdapterService productScoringAdapterService;

    public ProductService(Optional<ExternalCatalogClient> external,
                          Optional<ProductLookupPort> lookup,
                          Optional<ProductSnapshotPort> snapshot,
                          AsyncProductIngestionService asyncIngestionService,
                          Optional<ProductEvidenceReportService> productEvidenceReportService,
                          IngredientOnDemandResearchService ingredientOnDemandResearchService,
                          Optional<ProductScoringAdapterService> productScoringAdapterService) {
        this.external = external.orElse(null);
        this.lookup = lookup.orElse(null);
        this.snapshot = snapshot.orElse(null);
        this.asyncIngestionService = asyncIngestionService;
        this.productEvidenceReportService = productEvidenceReportService.orElse(null);
        this.ingredientOnDemandResearchService = ingredientOnDemandResearchService;
        this.productScoringAdapterService = productScoringAdapterService.orElse(null);

        log.info("ProductService wiring: external={}, lookup={}, snapshot={}",
                this.external != null ? this.external.getClass().getSimpleName() : "<none>",
                this.lookup   != null ? this.lookup.getClass().getSimpleName()   : "<none>",
                this.snapshot != null ? this.snapshot.getClass().getSimpleName() : "<none>");
    }

    /** Human-friendly provider name for logs & error responses (external side). */
    public String activeSourceName() {
        if (external == null) return "internal";
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
        if (code == null) return null;

        // 1) DB FIRST
        ProductDetailDto fromDbFirst = tryDbLookup(code);
        if (isStrictlyScored(fromDbFirst)) {
            int count = fromDbFirst.ingredients() == null ? 0 : fromDbFirst.ingredients().size();
            log.info("ProductService.getByGtinOrNull: using DB snapshot with {} ingredients for gtin={}", count, code);
            return fromDbFirst;
        }
        if (fromDbFirst != null) {
            reportLowCoverageIngredients(fromDbFirst);
            queueIngredientResearch(fromDbFirst);
            ProductDetailDto rescored = tryRescoreAndReload(code);
            if (isStrictlyScored(rescored)) {
                log.info("ProductService.getByGtinOrNull: returning rescored DB snapshot gtin={}", code);
                return rescored;
            }
            log.info("ProductService.getByGtinOrNull: returning existing DB snapshot without re-enrichment gtin={} strictScored=false", code);
            return fromDbFirst;
        }

        // 2) External if DB has nothing
        ProductDetailDto fromExternal = fetchFromExternal(code); // may throw 503
        if (fromExternal == null) {
            return null;
        }

        // 3) Kick off async snapshot/enrichment and return immediately.
        triggerAsyncSnapshot(fromExternal);

        log.info("ProductService.getByGtinOrNull: returning external result immediately for gtin={} while async ingestion runs", code);
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
            log.warn("goodbuy-db error on get gtin14={} type={} msg={}",
                    code, e.getClass().getSimpleName(), e.getMessage());
            return null;
        }
    }

    private void triggerAsyncSnapshot(ProductDetailDto dto) {
        if (dto == null) return;
        if (snapshot == null) {
            log.warn("ProductService.getByGtinOrNull: snapshot pipeline unavailable; serving external result only gtin={}", dto.gtin());
            return;
        }
        if (dto.ingredients() == null || dto.ingredients().isEmpty()) {
            log.warn("ProductService.getByGtinOrNull: external product has no ingredient list; skipping async ingestion gtin={}", dto.gtin());
            reportMissingIngredientList(dto);
            return;
        }
        asyncIngestionService.enqueue(dto);
    }

    /**
     * External fetch that distinguishes:
     *  - Optional.empty() => true miss
     *  - any exception => catalog down => 503
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

        } catch (Exception e) {
            long ms = Duration.between(t0, Instant.now()).toMillis();
            log.warn("catalog unavailable provider={} gtin14={} durMs={} type={} msg={}",
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

    private static boolean isStrictlyScored(ProductDetailDto dto) {
        return dto != null
                && dto.safetyScore() != null
                && dto.ratingLetter() != null
                && !dto.ratingLetter().isBlank()
                && !"NR".equalsIgnoreCase(dto.ratingLetter());
    }

    private void reportMissingIngredientList(ProductDetailDto dto) {
        if (dto == null || productEvidenceReportService == null) {
            return;
        }

        try {
            productEvidenceReportService.reportWithStatus(
                    dto.gtin(),
                    ProductEvidenceReportService.REASON_UNCLEAR_INGREDIENTS,
                    dto.name(),
                    dto.brand(),
                    "backend-ingestion",
                    "backend",
                    "External catalog returned no ingredient list.",
                    null,
                    null,
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("ProductService.reportMissingIngredientList: failed gtin={} err={}", dto.gtin(), ex.toString());
        }
    }

    private void reportLowCoverageIngredients(ProductDetailDto dto) {
        if (dto == null || productEvidenceReportService == null) {
            return;
        }
        if (dto.ingredients() == null || dto.ingredients().isEmpty()) {
            return;
        }

        try {
            String notes = "Product is missing full ingredient coverage. Raw ingredient labels: "
                    + dto.ingredients().stream()
                    .map(ing -> {
                        if (ing == null) return null;
                        if (ing.original() != null && !ing.original().isBlank()) return ing.original().trim();
                        if (ing.canonical() != null && !ing.canonical().isBlank()) return ing.canonical().trim();
                        if (ing.id() != null && !ing.id().isBlank()) return ing.id().trim();
                        return null;
                    })
                    .filter(java.util.Objects::nonNull)
                    .distinct()
                    .limit(50)
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("(none)");

            productEvidenceReportService.reportWithStatus(
                    dto.gtin(),
                    ProductEvidenceReportService.REASON_UNCLEAR_INGREDIENTS,
                    dto.name(),
                    dto.brand(),
                    "backend-ingestion",
                    "backend",
                    notes,
                    null,
                    null,
                    null,
                    null
            );
        } catch (Exception ex) {
            log.warn("ProductService.reportLowCoverageIngredients: failed gtin={} err={}", dto.gtin(), ex.toString());
        }
    }

    private void queueIngredientResearch(ProductDetailDto dto) {
        if (dto == null || dto.ingredients() == null || dto.ingredients().isEmpty()) {
            return;
        }

        dto.ingredients().stream()
                .filter(java.util.Objects::nonNull)
                .map(ing -> {
                    if (ing.canonical() != null && !ing.canonical().isBlank()) return ing.canonical().trim();
                    if (ing.original() != null && !ing.original().isBlank()) return ing.original().trim();
                    if (ing.id() != null && !ing.id().isBlank()) return ing.id().trim();
                    return null;
                })
                .filter(java.util.Objects::nonNull)
                .distinct()
                .limit(100)
                .forEach(ingredientOnDemandResearchService::getOrStartResearch);
    }

    private ProductDetailDto tryRescoreAndReload(String code) {
        if (productScoringAdapterService == null || lookup == null || code == null || code.isBlank()) {
            return null;
        }

        try {
            productScoringAdapterService.rescoreByEan(code);
            return tryDbLookup(code);
        } catch (Exception ex) {
            log.warn("ProductService.tryRescoreAndReload: failed gtin={} err={}", code, ex.toString());
            return null;
        }
    }

}
