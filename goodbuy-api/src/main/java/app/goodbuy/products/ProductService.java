package app.goodbuy.products;

import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import app.goodbuy.adapters.core.products.scoring.ProductScoringAdapterService;
import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.ingredients.scoring.IngredientScoreResult;
import app.goodbuy.ingredients.IngredientOnDemandResearchService;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import app.goodbuy.core.products.port.ProductLookupPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import app.goodbuy.core.products.scoring.ProductScoringEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    private final ProductScoringEngine productScoringEngine = new ProductScoringEngine();

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
            List<IngredientDTO> primedIngredients = primeIngredientCatalog(fromDbFirst);
            ProductDetailDto upgraded = tryExternalUpgrade(code, fromDbFirst);
            if (upgraded != null) {
                List<IngredientDTO> upgradedIngredients = primeIngredientCatalog(upgraded);
                triggerAsyncSnapshot(upgraded);
                ProductDetailDto enrichedExternal = withImmediateIngredientReads(upgraded, upgradedIngredients);
                log.info("ProductService.getByGtinOrNull: returning richer external product for gtin={} dbIngredients={} externalIngredients={}",
                        code, ingredientCount(fromDbFirst), ingredientCount(enrichedExternal));
                return enrichedExternal;
            }

            ProductDetailDto rescored = tryRescoreAndReload(code);
            if (isStrictlyScored(rescored)) {
                log.info("ProductService.getByGtinOrNull: returning rescored DB snapshot gtin={}", code);
                return rescored;
            }
            ProductDetailDto enrichedDb = withImmediateIngredientReads(fromDbFirst, primedIngredients);
            log.info("ProductService.getByGtinOrNull: returning existing DB snapshot without re-enrichment gtin={} strictScored=false", code);
            return enrichedDb;
        }

        // 2) External if DB has nothing
        ProductDetailDto fromExternal = fetchFromExternal(code); // may throw 503
        if (fromExternal == null) {
            return null;
        }

        List<IngredientDTO> primedIngredients = primeIngredientCatalog(fromExternal);

        // 3) Kick off async snapshot/enrichment and return immediately.
        triggerAsyncSnapshot(fromExternal);

        ProductDetailDto enrichedExternal = withImmediateIngredientReads(fromExternal, primedIngredients);
        log.info("ProductService.getByGtinOrNull: returning external result immediately for gtin={} while async ingestion runs", code);
        return enrichedExternal;
    }

    public ProductDetailDto getDetailByGtinOrNull(String gtin14) {
        return getByGtinOrNull(gtin14);
    }

    public List<IngredientDTO> resolveImmediateIngredientReads(ProductDetailDto dto) {
        return primeIngredientCatalog(dto);
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

    private ProductDetailDto tryExternalUpgrade(String code, ProductDetailDto existing) {
        if (external == null || existing == null) {
            return null;
        }

        int existingCount = ingredientCount(existing);
        try {
            ProductDetailDto externalDto = fetchFromExternal(code);
            if (externalDto == null) {
                return null;
            }

            return ingredientCount(externalDto) > existingCount ? externalDto : null;
        } catch (ResponseStatusException ex) {
            log.warn("ProductService.tryExternalUpgrade: external unavailable for gtin={} status={} msg={}",
                    code, ex.getStatusCode().value(), ex.getReason());
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
                    "External catalog returned no ingredient list."
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
                    notes
            );
        } catch (Exception ex) {
            log.warn("ProductService.reportLowCoverageIngredients: failed gtin={} err={}", dto.gtin(), ex.toString());
        }
    }

    private List<IngredientDTO> primeIngredientCatalog(ProductDetailDto dto) {
        if (dto == null || dto.ingredients() == null || dto.ingredients().isEmpty()) {
            return List.of();
        }

        return dto.ingredients().stream()
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
                .map(ingredientOnDemandResearchService::getOrStartResearch)
                .filter(Objects::nonNull)
                .toList();
    }

    private ProductDetailDto withProvisionalProductScore(ProductDetailDto dto, List<IngredientDTO> primedIngredients) {
        if (dto == null || isStrictlyScored(dto)) {
            return dto;
        }

        List<IngredientScoreResult> scoredIngredients = primedIngredients == null
                ? List.of()
                : primedIngredients.stream()
                .filter(Objects::nonNull)
                .filter(ing -> ing.safetyScore() != null && ing.ratingLetter() != null && !ing.ratingLetter().isBlank())
                .map(ing -> new IngredientScoreResult(
                        ing.safetyScore().intValue(),
                        ing.ratingLetter(),
                        List.of("Provisional product scoring used ingredient-level GoodBuy reads available at scan time.")
                ))
                .toList();

        if (scoredIngredients.isEmpty()) {
            return dto;
        }

        var provisional = productScoringEngine.score(scoredIngredients, dto.domain());
        if (!provisional.ratingLetter().isBlank() && !"NR".equalsIgnoreCase(provisional.ratingLetter())) {
            return new ProductDetailDto(
                    dto.gtin(),
                    dto.name(),
                    dto.brand(),
                    dto.category(),
                    dto.description(),
                    dto.images(),
                    dto.ingredients(),
                    dto.titles(),
                    dto.manufacturer(),
                    dto.source(),
                    dto.domain(),
                    BigDecimal.valueOf(provisional.safetyScore()),
                    provisional.ratingLetter()
            );
        }

        return dto;
    }

    private ProductDetailDto withImmediateIngredientReads(ProductDetailDto dto, List<IngredientDTO> primedIngredients) {
        if (dto == null) {
            return null;
        }

        List<ProductDetailDto.IngredientDto> enrichedIngredients = overlayImmediateIngredientReads(dto, primedIngredients);
        ProductDetailDto enrichedDto = new ProductDetailDto(
                dto.gtin(),
                dto.name(),
                dto.brand(),
                dto.category(),
                dto.description(),
                dto.images(),
                enrichedIngredients,
                dto.titles(),
                dto.manufacturer(),
                dto.source(),
                dto.domain(),
                dto.safetyScore(),
                dto.ratingLetter()
        );

        return withProvisionalProductScore(enrichedDto, primedIngredients);
    }

    private List<ProductDetailDto.IngredientDto> overlayImmediateIngredientReads(ProductDetailDto dto, List<IngredientDTO> primedIngredients) {
        if (dto == null || dto.ingredients() == null || dto.ingredients().isEmpty()) {
            return dto == null || dto.ingredients() == null ? List.of() : dto.ingredients();
        }
        if (primedIngredients == null || primedIngredients.isEmpty()) {
            return dto.ingredients();
        }

        Map<String, IngredientDTO> indexed = new LinkedHashMap<>();
        for (IngredientDTO ingredient : primedIngredients) {
            if (ingredient == null) {
                continue;
            }
            putImmediateIngredient(indexed, ingredient.canonicalKey(), ingredient);
            putImmediateIngredient(indexed, ingredient.displayName(), ingredient);
            if (ingredient.aliases() != null) {
                ingredient.aliases().forEach(alias -> putImmediateIngredient(indexed, alias, ingredient));
            }
        }

        return dto.ingredients().stream()
                .map(ingredient -> overlayImmediateIngredientRead(ingredient, indexed))
                .toList();
    }

    private ProductDetailDto.IngredientDto overlayImmediateIngredientRead(ProductDetailDto.IngredientDto ingredient,
                                                                          Map<String, IngredientDTO> indexed) {
        if (ingredient == null) {
            return null;
        }

        String label = firstNonBlank(ingredient.original(), ingredient.canonical(), ingredient.id());
        IngredientDTO match = findImmediateIngredient(indexed, ingredient.id(), ingredient.canonical(), ingredient.original());
        if (match == null) {
            return ingredient;
        }

        return new ProductDetailDto.IngredientDto(
                firstNonBlank(match.canonicalKey(), ingredient.id(), label),
                firstNonBlank(ingredient.original(), label, match.displayName()),
                firstNonBlank(match.displayName(), ingredient.canonical(), label),
                ingredient.externalIds(),
                ingredient.isVegan(),
                ingredient.isVegetarian()
        );
    }

    private IngredientDTO findImmediateIngredient(Map<String, IngredientDTO> indexed, String... candidates) {
        if (indexed == null || indexed.isEmpty() || candidates == null) {
            return null;
        }

        for (String candidate : candidates) {
            String normalized = normalizeIngredientKey(candidate);
            if (normalized == null) {
                continue;
            }

            IngredientDTO ingredient = indexed.get(normalized);
            if (ingredient != null) {
                return ingredient;
            }
        }

        return null;
    }

    private void putImmediateIngredient(Map<String, IngredientDTO> indexed, String key, IngredientDTO ingredient) {
        String normalized = normalizeIngredientKey(key);
        if (normalized != null && ingredient != null) {
            indexed.putIfAbsent(normalized, ingredient);
        }
    }

    private String normalizeIngredientKey(String value) {
        if (value == null) {
            return null;
        }

        String normalized = value
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ")
                .replace('’', '\'')
                .replace('–', '-')
                .replace('—', '-');

        return normalized.isEmpty() ? null : normalized;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }

        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }

        return null;
    }

    private static int ingredientCount(ProductDetailDto dto) {
        return (dto == null || dto.ingredients() == null) ? 0 : dto.ingredients().size();
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
