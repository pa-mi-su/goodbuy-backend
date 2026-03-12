package app.goodbuy.adapters.core.products.snapshot;

import app.goodbuy.adapters.core.citations.service.IngredientCitationWriter;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientAlias;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.service.IngredientCreationService;
import app.goodbuy.adapters.core.ingredients.service.IngredientSignalsWriter;
import app.goodbuy.adapters.core.ingredients.service.MissingIngredientReportService;
import app.goodbuy.adapters.core.notifications.SlackNotificationAdapter;
import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.model.ProductIngredientEntity;
import app.goodbuy.adapters.core.products.repo.ProductIngredientRepository;
import app.goodbuy.adapters.core.products.repo.ProductRepository;
import app.goodbuy.adapters.core.products.scoring.ProductScoringAdapterService;
import app.goodbuy.core.ingredients.port.IngredientAutoEnricherPort;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentRequest;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentResult;
import app.goodbuy.core.products.StrictProductIngestionException;
import app.goodbuy.core.products.domain.ProductDomain;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductDomainConfigPort;
import app.goodbuy.core.products.port.ProductDomainResolverPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import app.goodbuy.core.storage.ProductImageStoragePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class DbProductSnapshotAdapter implements ProductSnapshotPort {

    private static final Logger log = LoggerFactory.getLogger(DbProductSnapshotAdapter.class);

    /**
     * NOTE: adapters-core must not reference concrete implementations from other modules.
     * We therefore only parse best-effort PubChem metadata from the enricher's note string.
     *
     * Convention (recommended):
     *   note = "pubchem_cid=123;pubchem_mutagen=true;pubchem_reproductive_toxin=false;..."
     */
    private static final Pattern NOTE_PUBCHEM_CID = Pattern.compile("\\bpubchem_cid=(\\d+)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTE_PUBCHEM_MUTAGEN = Pattern.compile("\\bpubchem_mutagen=(true|false)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern NOTE_PUBCHEM_REPRO = Pattern.compile("\\bpubchem_reproductive_toxin=(true|false)\\b", Pattern.CASE_INSENSITIVE);

    private static final String PROVIDER_PUBCHEM = "PUBCHEM";

    private final ProductRepository productRepo;
    private final ProductIngredientRepository productIngredientRepo;
    private final IngredientRepository ingredientRepo;
    private final ProductImageStoragePort imageStorage;
    private final ProductDomainResolverPort domainResolver;
    private final ProductDomainConfigPort domainConfig;
    private final ProductScoringAdapterService productScoringAdapter;
    private final MissingIngredientReportService missingIngredientReportService;
    private final IngredientCreationService ingredientCreationService;
    private final SlackNotificationAdapter slackNotificationAdapter;

    private final IngredientCitationWriter citationWriter;
    private final IngredientSignalsWriter signalsWriter;

    // Optional: only present if auto-enrichment is enabled + wired
    private final IngredientAutoEnricherPort autoEnricher; // may be null

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @PersistenceContext
    private EntityManager em;

    public DbProductSnapshotAdapter(
            ProductRepository productRepo,
            ProductIngredientRepository productIngredientRepo,
            IngredientRepository ingredientRepo,
            ProductImageStoragePort imageStorage,
            ProductDomainResolverPort domainResolver,
            ProductDomainConfigPort domainConfig,
            ProductScoringAdapterService productScoringAdapter,
            MissingIngredientReportService missingIngredientReportService,
            IngredientCreationService ingredientCreationService,
            SlackNotificationAdapter slackNotificationAdapter,
            IngredientCitationWriter citationWriter,
            IngredientSignalsWriter signalsWriter,
            Optional<IngredientAutoEnricherPort> autoEnricher
    ) {
        this.productRepo = productRepo;
        this.productIngredientRepo = productIngredientRepo;
        this.ingredientRepo = ingredientRepo;
        this.imageStorage = imageStorage;
        this.domainResolver = domainResolver;
        this.domainConfig = domainConfig;
        this.productScoringAdapter = productScoringAdapter;
        this.missingIngredientReportService = missingIngredientReportService;
        this.ingredientCreationService = ingredientCreationService;
        this.slackNotificationAdapter = slackNotificationAdapter;
        this.citationWriter = citationWriter;
        this.signalsWriter = signalsWriter;
        this.autoEnricher = autoEnricher.orElse(null);

        log.info("DbProductSnapshotAdapter wiring: autoEnricher={}",
                this.autoEnricher == null ? "<none>" : this.autoEnricher.getClass().getSimpleName());
    }

    @Override
    @Transactional
    public void saveSnapshot(ProductDetailDto dto) {
        if (dto == null) {
            log.warn("saveSnapshot: DTO was null — skipping");
            return;
        }

        String rawGtin = dto.gtin();
        String ean14 = normalizeToGtin14(rawGtin);
        if (ean14 == null) {
            log.warn("saveSnapshot: invalid gtin={} — skipping", rawGtin);
            return;
        }

        log.info("saveSnapshot: BEGIN gtin={} name='{}' brand='{}'",
                ean14, safe(dto.name()), safe(dto.brand()));

        ProductEntity product = productRepo.findByEan(ean14).orElseGet(ProductEntity::new);

        boolean isNew = (product.getId() == null);
        if (isNew) product.setEan(ean14);

        product.setName(dto.name());
        product.setBrand(dto.brand());
        product.setCategory(dto.category());
        product.setDescription(dto.description());
        product.setRawIngredientText(buildRawIngredientText(dto.ingredients()));

        String existingDomain = product.getDomain();

        ProductDomain domainEnum = domainResolver.classify(
                existingDomain,
                product.getCategory(),
                product.getName(),
                product.getBrand()
        );

        String domainCode = (domainEnum == null ? ProductDomain.UNKNOWN.code() : domainEnum.code());
        product.setDomain(domainCode);

        boolean enabled = domainConfig.isEnabled(domainCode);
        boolean rated = domainConfig.isRated(domainCode);

        log.info("saveSnapshot: classified domain={} enabled={} rated={} gtin={}",
                domainCode, enabled, rated, ean14);

        // ───────────── IMAGE SELECTION + S3 MIRROR ─────────────
        List<ProductDetailDto.ImageDto> images = dto.images();
        int imageCount = (images == null) ? 0 : images.size();
        log.info("saveSnapshot: image_count={} for gtin={}", imageCount, ean14);

        String primaryExternalUrl = null;
        if (images != null) {
            primaryExternalUrl = images.stream()
                    .map(ProductDetailDto.ImageDto::url)
                    .filter(u -> u != null && !u.isBlank())
                    .findFirst()
                    .orElse(null);
        }

        log.info("saveSnapshot: chosen external_image_url={} for gtin={}",
                safe(primaryExternalUrl), ean14);
        product.setPrimaryImageUrl(primaryExternalUrl);

        if (primaryExternalUrl != null && !primaryExternalUrl.isBlank()) {
            try {
                log.info("saveSnapshot: starting S3 mirror for gtin={} url={}", ean14, primaryExternalUrl);
                String s3Url = mirrorExternalImageToS3(ean14, primaryExternalUrl.trim());
                product.setPrimaryImageS3Url(s3Url);
                log.info("saveSnapshot: S3 upload COMPLETE gtin={} s3Url={}", ean14, s3Url);
            } catch (Exception ex) {
                log.warn("saveSnapshot: S3 upload FAILED for gtin={} url={} err={}",
                        ean14, primaryExternalUrl, ex.toString());
            }
        } else {
            log.info("saveSnapshot: NO external image URL for gtin={} — skipping S3 upload", ean14);
        }

        product = productRepo.save(product);
        log.info("saveSnapshot: product persisted id={} ean={} (isNew={}) domain={} enabled={} rated={} s3Url={}",
                product.getId(), product.getEan(), isNew, product.getDomain(), enabled, rated,
                safe(product.getPrimaryImageS3Url()));

        if (!enabled) {
            log.info("saveSnapshot: domain={} not enabled — skipping ingredients + scoring. gtin={}", domainCode, ean14);
            return;
        }

        List<ProductDetailDto.IngredientDto> dtoIngredients = dto.ingredients();
        if (dtoIngredients == null || dtoIngredients.isEmpty()) {
            throw new StrictProductIngestionException("External catalog returned no ingredient list for product " + ean14);
        }

        if (!isNew && product.getId() != null) {
            int removed = em.createQuery("""
                    delete from ProductIngredientEntity pi
                    where pi.product = :product
                    """)
                    .setParameter("product", product)
                    .executeUpdate();
            log.info("saveSnapshot: cleared {} existing product_ingredients for productId={}", removed, product.getId());
        }

        if (product.getProductIngredients() != null) {
            product.getProductIngredients().clear();
        }

        int linked = 0;
        Set<String> seenKeysThisSnapshot = new HashSet<>();

        for (ProductDetailDto.IngredientDto ing : dtoIngredients) {
            if (ing == null) continue;

            String displayName = firstNonBlank(ing.original(), ing.canonical(), ing.id());
            if (displayName == null) continue;

            if (isNonIngredientToken(displayName)) {
                log.info("saveSnapshot: skipping non-ingredient token displayName='{}' gtin={}",
                        safe(displayName), ean14);
                continue;
            }

            String rawKeyCandidate = firstNonBlank(ing.canonical(), ing.original(), ing.id());
            if (rawKeyCandidate == null) continue;

            if (isNonIngredientToken(rawKeyCandidate)) {
                log.info("saveSnapshot: skipping non-ingredient token canonicalCandidate='{}' gtin={}",
                        safe(rawKeyCandidate), ean14);
                continue;
            }

            String canonicalKey = normalizeCanonicalKey(rawKeyCandidate);

            if (!seenKeysThisSnapshot.add(canonicalKey)) {
                continue;
            }

            Ingredient ingredient = resolveOrCreateIngredient(
                    canonicalKey,
                    displayName,
                    ean14,
                    ing.externalIds()
            );

            if (ingredient == null || ingredient.getId() == null) {
                log.warn("saveSnapshot: ingredient resolution failed for canonicalKey='{}' — skipping link", canonicalKey);
                continue;
            }

            ProductIngredientEntity link = new ProductIngredientEntity();
            link.setProduct(product);
            link.setIngredient(ingredient);
            link.setDisplayName(displayName);

            link = productIngredientRepo.save(link);
            product.getProductIngredients().add(link);

            linked++;
        }

        log.info("saveSnapshot: linked {} NEW ingredients → gtin={}", linked, ean14);

        em.flush();
        log.debug("saveSnapshot: JPA flush complete before product scoring for gtin={}", ean14);

        if (!rated) {
            log.info("saveSnapshot: domain={} enabled but not rated — skipping product scoring. gtin={}", domainCode, ean14);
            return;
        }

        try {
            var scoreResult = productScoringAdapter.scoreProduct(product);
            log.info("saveSnapshot: product score computed gtin={} result={}", ean14, scoreResult);
        } catch (Exception ex) {
            if (ex instanceof StrictProductIngestionException strict) {
                throw strict;
            }
            log.warn("saveSnapshot: product scoring FAILED for gtin={} err={}", ean14, ex.toString());
            throw new StrictProductIngestionException("Product scoring failed for " + ean14, ex);
        }
    }

    private Ingredient resolveOrCreateIngredient(
            String canonicalKey,
            String displayName,
            String productEan,
            Map<String, String> externalIds
    ) {
        if (isNonIngredientToken(canonicalKey) || isNonIngredientToken(displayName)) {
            log.info("resolveOrCreateIngredient: skipping non-ingredient token canonicalKey='{}' displayName='{}' ean={}",
                    safe(canonicalKey), safe(displayName), productEan);
            return null;
        }

        Ingredient ingredient = ingredientRepo.findByCanonicalKeyIgnoreCase(canonicalKey).orElse(null);

        if (ingredient == null) {
            List<Ingredient> candidates = ingredientRepo.findByAllNormalized(canonicalKey);
            if (!candidates.isEmpty()) {
                ingredient = candidates.get(0);
                log.info("saveSnapshot: matched ingredient via alias/name needle='{}' → id={} canonicalKey='{}'",
                        canonicalKey, ingredient.getId(), safe(ingredient.getCanonicalKey()));
            }
        }

        if (ingredient != null) {
            if (shouldAttemptEnrichmentOnExisting(ingredient) && autoEnricher != null) {
                tryEnrichExistingIngredient(ingredient, canonicalKey, displayName, productEan, externalIds);
            }
            return ingredient;
        }

        IngredientEnrichmentResult enr = null;
        boolean providerEnriched = false;

        String enrichmentQuery = deriveEnrichmentQuery(displayName, canonicalKey);

        if (autoEnricher == null) {
            log.info("resolveOrCreateIngredient: auto-enrich SKIPPED (no enricher wired) canonicalKey='{}' ean={}",
                    canonicalKey, productEan);
        } else {
            long t0 = System.currentTimeMillis();
            log.info("resolveOrCreateIngredient: auto-enrich ATTEMPT canonicalKey='{}' query='{}' ean={} externalIds={}",
                    canonicalKey, safe(enrichmentQuery), productEan, externalIds == null ? 0 : externalIds.size());
            try {
                enr = autoEnricher.enrich(new IngredientEnrichmentRequest(
                        canonicalKey,
                        enrichmentQuery,
                        externalIds,
                        "EAN-DB",
                        productEan
                ));

                providerEnriched = (enr != null && enr.enriched());
                long dur = System.currentTimeMillis() - t0;

                log.info("resolveOrCreateIngredient: auto-enrich RESULT canonicalKey='{}' providerOk={} durMs={} provider={} note={}",
                        canonicalKey,
                        providerEnriched,
                        dur,
                        enr == null ? "(null)" : safe(enr.provider()),
                        enr == null ? "(null)" : safe(enr.note())
                );

            } catch (Exception ex) {
                long dur = System.currentTimeMillis() - t0;
                log.warn("resolveOrCreateIngredient: auto-enrich FAILED canonicalKey='{}' query='{}' ean={} durMs={} err={}",
                        canonicalKey, safe(enrichmentQuery), productEan, dur, ex.toString());
            }
        }

        Ingredient created = new Ingredient();
        created.setCanonicalKey(canonicalKey);
        created.setActive(true);
        created.setDisplayName(firstNonBlank(displayName, canonicalKey));
        if (providerEnriched && enr != null) {
            applyEnrichmentToIngredient(created, enr, displayName);
        }
        try {
            Long ingredientId = ingredientCreationService.createIngredient(created);
            ingredient = ingredientRepo.findById(ingredientId).orElse(null);
        } catch (DataIntegrityViolationException ex) {
            ingredient = ingredientRepo.findByCanonicalKeyIgnoreCase(canonicalKey).orElse(null);
            if (ingredient == null) {
                throw ex;
            }
            log.info("resolveOrCreateIngredient: reused concurrently-created ingredient id={} canonicalKey='{}' ean={}",
                    ingredient.getId(), canonicalKey, productEan);
        }

        if (ingredient == null || ingredient.getId() == null) {
            throw new StrictProductIngestionException(
                    "Ingredient row unavailable after concurrent creation for '" + displayName + "' (" + canonicalKey + ")."
            );
        }

        boolean dbEnriched = false;
        if (providerEnriched && enr != null) {
            attachCitations(ingredient, canonicalKey, enr);
            dbEnriched = writeSignalsAndScore(ingredient, canonicalKey, productEan, enr);
        } else {
            notifyIngredientAttention(
                    canonicalKey,
                    displayName,
                    productEan,
                    "Ingredient created as skeleton; no structured enrichment available yet."
            );
        }

        try {
            missingIngredientReportService.reportWithStatus(
                    ingredient.getDisplayName(),
                    productEan,
                    "backend-ingestion",
                    "backend",
                    dbEnriched
                            ? "Unmatched ingredient auto-enriched+created from scan (canonicalKey=" + canonicalKey + ")"
                            : (providerEnriched
                            ? "Unmatched ingredient enrichment attempted but NOT DB-confirmed (canonicalKey=" + canonicalKey + ")"
                            : "Unmatched ingredient created as skeleton from scan (canonicalKey=" + canonicalKey + ")")
            );
        } catch (Exception ex) {
            log.warn("Failed to record missing ingredient report for '{}' (ean={}): {}",
                    ingredient.getDisplayName(), productEan, ex.getMessage(), ex);
        }

        ingredient = ingredientRepo.saveAndFlush(ingredient);
        return ingredient;
    }

    private boolean shouldAttemptEnrichmentOnExisting(Ingredient i) {
        return !isIngredientStrictlyReady(i);
    }

    private void tryEnrichExistingIngredient(
            Ingredient ingredient,
            String canonicalKey,
            String displayName,
            String productEan,
            Map<String, String> externalIds
    ) {
        String enrichmentQuery = deriveEnrichmentQuery(displayName, canonicalKey);

        IngredientEnrichmentResult enr;
        boolean providerEnriched;

        long t0 = System.currentTimeMillis();
        try {
            enr = autoEnricher.enrich(new IngredientEnrichmentRequest(
                    canonicalKey,
                    enrichmentQuery,
                    externalIds,
                    "EAN-DB",
                    productEan
            ));
            providerEnriched = (enr != null && enr.enriched());
        } catch (Exception ex) {
            long dur = System.currentTimeMillis() - t0;
            log.warn("resolveOrCreateIngredient: existing skeleton → auto-enrich FAILED ingredientId={} canonicalKey='{}' durMs={} err={}",
                    ingredient.getId(), canonicalKey, dur, ex.toString());
            return;
        }

        if (!providerEnriched || enr == null) return;

        applyEnrichmentToIngredient(ingredient, enr, displayName);
        ingredientRepo.saveAndFlush(ingredient);
        attachCitations(ingredient, canonicalKey, enr);
        boolean dbEnriched = writeSignalsAndScore(ingredient, canonicalKey, productEan, enr);

        ingredientRepo.saveAndFlush(ingredient);
    }

    private void applyEnrichmentToIngredient(Ingredient ingredient, IngredientEnrichmentResult enrichment, String fallbackDisplayName) {
        ingredient.setDisplayName(firstNonBlank(enrichment.displayName(), fallbackDisplayName, ingredient.getDisplayName(), ingredient.getCanonicalKey()));
        ingredient.setSummary(firstNonBlank(enrichment.summary(), ingredient.getSummary()));
        ingredient.setDescription(firstNonBlank(enrichment.description(), ingredient.getDescription()));
        ingredient.setFuncUse(firstNonBlank(enrichment.functionUse(), ingredient.getFuncUse()));
        ingredient.setConcerns(firstNonBlank(enrichment.concerns(), ingredient.getConcerns()));
        ingredient.setCategory(firstNonBlank(enrichment.category(), ingredient.getCategory()));
        ingredient.setRegulationNotes(firstNonBlank(enrichment.regulationNotes(), ingredient.getRegulationNotes()));
        ingredient.setReferencesCount(
                enrichment.referencesCount() != null
                        ? enrichment.referencesCount()
                        : Math.max(
                                ingredient.getReferencesCount() == null ? 0 : ingredient.getReferencesCount(),
                                enrichment.sourceUrls() == null ? 0 : enrichment.sourceUrls().size()
                        )
        );
        mergeAliases(ingredient, enrichment.aliases());
        mergeTags(ingredient, enrichment.tags());
    }

    private void mergeAliases(Ingredient ingredient, List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) return;

        Set<String> existing = ingredient.getAliases().stream()
                .map(IngredientAlias::getAlias)
                .filter(Objects::nonNull)
                .map(DbProductSnapshotAdapter::normalizeCanonicalKey)
                .collect(java.util.stream.Collectors.toSet());

        for (String alias : aliases) {
            String normalized = normalizeCanonicalKey(alias);
            if (normalized.isBlank()) continue;
            if (normalized.equals(normalizeCanonicalKey(ingredient.getCanonicalKey()))) continue;
            if (!existing.add(normalized)) continue;

            IngredientAlias row = new IngredientAlias();
            row.setIngredient(ingredient);
            row.setAlias(alias.trim());
            ingredient.getAliases().add(row);
        }
    }

    private void mergeTags(Ingredient ingredient, List<String> tags) {
        if (tags == null || tags.isEmpty()) return;

        Set<String> merged = new java.util.LinkedHashSet<>();
        if (ingredient.getTags() != null) {
            ingredient.getTags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(merged::add);
        }
        tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(merged::add);
        ingredient.setTags(new ArrayList<>(merged));
    }

    private void attachCitations(Ingredient ingredient, String canonicalKey, IngredientEnrichmentResult enrichment) {
        if (ingredient == null || ingredient.getId() == null) return;
        if (enrichment == null || enrichment.sourceUrls() == null || enrichment.sourceUrls().isEmpty()) return;

        String src = isBlank(enrichment.provider()) ? "Unknown" : enrichment.provider().trim();
        try {
            citationWriter.attachCitations(ingredient.getId(), src, enrichment.sourceUrls(), enrichment.citationTitle());
        } catch (Exception ex) {
            log.warn("resolveOrCreateIngredient: attach citations failed ingredientId={} canonicalKey='{}' err={}",
                    ingredient.getId(), canonicalKey, ex.toString());
        }
    }

    private boolean writeSignalsAndScore(
            Ingredient ingredient,
            String canonicalKey,
            String productEan,
            IngredientEnrichmentResult enrichment
    ) {
        try {
            boolean written = signalsWriter.upsertSignalsAndScore(ingredient.getId(), enrichment);
            if (!written) {
                notifyIngredientAttention(
                        canonicalKey,
                        ingredient.getDisplayName(),
                        productEan,
                        "Signal persistence/scoring did not complete."
                );
                throw new StrictProductIngestionException(
                        "Ingredient scoring failed for '" + ingredient.getDisplayName() + "' (" + canonicalKey + ")."
                );
            }
            return true;
        } catch (StrictProductIngestionException ex) {
            throw ex;
        } catch (Exception ex) {
            notifyIngredientAttention(
                    canonicalKey,
                    ingredient.getDisplayName(),
                    productEan,
                    "Signal persistence/scoring threw " + ex.getClass().getSimpleName() + ": " + ex.getMessage()
            );
            throw new StrictProductIngestionException(
                    "Ingredient scoring failed for '" + ingredient.getDisplayName() + "' (" + canonicalKey + ").",
                    ex
            );
        }
    }

    private void assertIngredientStrictlyReady(Ingredient ingredient, String canonicalKey, String productEan) {
        if (ingredient != null && isIngredientStrictlyReady(ingredient)) {
            return;
        }

        String missing = ingredient == null ? "ingredient_row_missing" : String.join(", ", missingIngredientFields(ingredient));
        notifyIngredientAttention(canonicalKey, ingredient == null ? canonicalKey : ingredient.getDisplayName(), productEan, missing);
        throw new StrictProductIngestionException(
                "Ingredient '" + canonicalKey + "' is not complete after ingestion: " + missing
        );
    }

    private boolean isIngredientStrictlyReady(Ingredient ingredient) {
        return ingredient != null
                && !isBlank(ingredient.getDisplayName())
                && !isBlank(ingredient.getSummary())
                && !isBlank(ingredient.getDescription())
                && !isBlank(ingredient.getFuncUse())
                && !isBlank(ingredient.getConcerns())
                && !isBlank(ingredient.getCategory())
                && !isBlank(ingredient.getRegulationNotes())
                && ingredient.getReferencesCount() != null;
    }

    private List<String> missingIngredientFields(Ingredient ingredient) {
        List<String> missing = new ArrayList<>();
        if (isBlank(ingredient.getDisplayName())) missing.add("display_name");
        if (isBlank(ingredient.getSummary())) missing.add("summary");
        if (isBlank(ingredient.getDescription())) missing.add("description");
        if (isBlank(ingredient.getFuncUse())) missing.add("func_use");
        if (isBlank(ingredient.getConcerns())) missing.add("concerns");
        if (isBlank(ingredient.getCategory())) missing.add("category");
        if (isBlank(ingredient.getRegulationNotes())) missing.add("regulation_notes");
        if (ingredient.getReferencesCount() == null) missing.add("references_count");
        return missing;
    }

    private void notifyIngredientAttention(String canonicalKey, String displayName, String productEan, String reason) {
        String message = """
                :rotating_light: Ingredient needs manual enrichment
                product_ean: %s
                canonical_key: %s
                display_name: %s
                reason: %s
                """.formatted(
                safe(productEan),
                safe(canonicalKey),
                safe(displayName),
                safe(reason)
        );
        slackNotificationAdapter.sendIngredientMissing(message);
    }

    private String mirrorExternalImageToS3(String ean14, String externalUrl) throws Exception {
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(externalUrl))
                .GET()
                .build();

        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        int status = resp.statusCode();
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + " from image host");
        }

        byte[] bytes = resp.body();
        String contentType = resp.headers().firstValue("Content-Type").orElse("image/jpeg");

        String key = "catalog/" + ean14 + "/primary/" +
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + "_" +
                UUID.randomUUID().toString().replace("-", "") + ".jpg";

        return imageStorage.uploadImage(key, bytes, contentType);
    }

    private static String normalizeToGtin14(String raw) {
        if (raw == null) return null;
        String digits = raw.trim();
        if (!digits.matches("\\d+")) return null;
        return switch (digits.length()) {
            case 14 -> digits;
            case 13 -> "0" + digits;
            case 12 -> "00" + digits;
            default -> null;
        };
    }

    private static String normalizeCanonicalKey(String raw) {
        String x = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        x = x.replaceAll("\\s+", " ");
        return x;
    }

    private static String buildRawIngredientText(List<ProductDetailDto.IngredientDto> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return null;
        }

        List<String> labels = ingredients.stream()
                .filter(Objects::nonNull)
                .map(ing -> firstNonBlank(ing.original(), ing.canonical(), ing.id()))
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        if (labels.isEmpty()) {
            return null;
        }

        return String.join(", ", labels);
    }

    private static String deriveEnrichmentQuery(String displayName, String canonicalKey) {
        String base = firstNonBlank(displayName, canonicalKey);
        if (base == null) return null;

        String x = base.trim();
        x = x.replaceAll("\\s*\\([^)]*\\)\\s*", " ").trim();
        x = x.replaceAll("\\b(softgel|softgels|capsule|capsules|tablet|tablets|gummy|gummies|flavor|unflavored)\\b", " ");
        x = x.replaceAll("\\s+", " ").trim();
        x = x.replace('’', '\'').replace('–', '-').replace('—', '-');

        return x.isBlank() ? base : x;
    }

    private static boolean isNonIngredientToken(String s) {
        if (s == null) return true;

        String x = s.trim();
        if (x.isBlank()) return true;

        String lower = x.toLowerCase(Locale.ROOT);

        if (lower.matches("^[\\p{Punct}\\s]+$")) return true;
        if (lower.endsWith(":")) return true;

        if (lower.contains("less than") && lower.contains("%")) return true;
        if (lower.contains("contains") && lower.contains("less than")) return true;

        if (lower.matches(".*\\b\\d+\\s*%\\s*(or\\s*less|and\\s*under)\\b.*")) return true;
        if (lower.matches(".*<\\s*\\d+\\s*%.*")) return true;

        if (lower.startsWith("other ingredients")) return true;
        if (lower.startsWith("inactive ingredients")) return true;
        if (lower.startsWith("active ingredients")) return true;
        if (lower.startsWith("contains:")) return true;

        return false;
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "(null)" : s;
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isBlank();
    }
}
