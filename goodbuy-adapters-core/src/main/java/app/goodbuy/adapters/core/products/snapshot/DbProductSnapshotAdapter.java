package app.goodbuy.adapters.core.products.snapshot;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.service.MissingIngredientReportService;
import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.model.ProductIngredientEntity;
import app.goodbuy.adapters.core.products.repo.ProductIngredientRepository;
import app.goodbuy.adapters.core.products.repo.ProductRepository;
import app.goodbuy.adapters.core.products.scoring.ProductScoringAdapterService;
import app.goodbuy.core.products.domain.ProductDomain;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductDomainResolverPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import app.goodbuy.core.storage.ProductImageStoragePort;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Component
public class DbProductSnapshotAdapter implements ProductSnapshotPort {

    private static final Logger log = LoggerFactory.getLogger(DbProductSnapshotAdapter.class);

    private final ProductRepository productRepo;
    private final ProductIngredientRepository productIngredientRepo;
    private final IngredientRepository ingredientRepo;
    private final ProductImageStoragePort imageStorage;
    private final ProductDomainResolverPort domainResolver;
    private final ProductScoringAdapterService productScoringAdapter;
    private final MissingIngredientReportService missingIngredientReportService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @PersistenceContext
    private EntityManager em;

    public DbProductSnapshotAdapter(
            ProductRepository productRepo,
            ProductIngredientRepository productIngredientRepo,
            IngredientRepository ingredientRepo,
            ProductImageStoragePort imageStorage,
            ProductDomainResolverPort domainResolver,
            ProductScoringAdapterService productScoringAdapter,
            MissingIngredientReportService missingIngredientReportService
    ) {
        this.productRepo = productRepo;
        this.productIngredientRepo = productIngredientRepo;
        this.ingredientRepo = ingredientRepo;
        this.imageStorage = imageStorage;
        this.domainResolver = domainResolver;
        this.productScoringAdapter = productScoringAdapter;
        this.missingIngredientReportService = missingIngredientReportService;
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

        // ───────────────── PRODUCT UPSERT ─────────────────
        ProductEntity product = productRepo.findByEan(ean14)
                .orElseGet(ProductEntity::new);

        boolean isNew = (product.getId() == null);
        if (isNew) {
            product.setEan(ean14);
        }

        product.setName(dto.name());
        product.setBrand(dto.brand());
        product.setCategory(dto.category());
        product.setDescription(dto.description());

        // ───────────── DOMAIN CLASSIFICATION (PERSISTED) ─────────────
        String existingDomain = product.getDomain(); // may be "unknown" or null

        ProductDomain domainEnum = domainResolver.classify(
                existingDomain,
                product.getCategory(),
                product.getName(),
                product.getBrand()
        );

        String domainStr = domainEnum.name().toLowerCase(Locale.ROOT);
        product.setDomain(domainStr);

        log.info("saveSnapshot: classified domain={} for gtin={}", domainStr, ean14);

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

        // Optional S3 mirror for primary image (best effort, non-fatal)
        if (primaryExternalUrl != null && !primaryExternalUrl.isBlank()) {
            try {
                log.info("saveSnapshot: starting S3 mirror for gtin={} url={}",
                        ean14, primaryExternalUrl);

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

        // ───────────── SAVE PRODUCT (so it has an ID) ─────────────
        product = productRepo.save(product);
        log.info("saveSnapshot: product persisted id={} ean={} (isNew={}) domain={} s3Url={}",
                product.getId(), product.getEan(), isNew, product.getDomain(), safe(product.getPrimaryImageS3Url()));

        // ✅ BUGFIX #1:
        // If this product is NOT in a supported domain, DO NOT seed ingredients and DO NOT report missing ingredients.
        // This prevents "unsupported domain" items from being treated like "missing ingredients."
        if (!isSupportedDomain(domainEnum)) {
            log.info("saveSnapshot: domain={} is not supported for ingredient seeding. Skipping ingredients + scoring. gtin={}",
                    domainEnum.name(), ean14);
            return;
        }

        // ───────────── INGREDIENT LINKS (SKELETON SEEDING) ─────────────
        List<ProductDetailDto.IngredientDto> dtoIngredients = dto.ingredients();
        if (dtoIngredients == null || dtoIngredients.isEmpty()) {
            log.info("saveSnapshot: no ingredients for gtin={} — DONE", ean14);
            return;
        }

        // Clear ALL existing product_ingredients for this product
        if (!isNew && product.getId() != null) {
            int removed = em.createQuery("""
                    delete from ProductIngredientEntity pi
                    where pi.product = :product
                    """)
                    .setParameter("product", product)
                    .executeUpdate();
            log.info("saveSnapshot: cleared {} existing product_ingredients for productId={}",
                    removed, product.getId());
        }

        // Also clear the in-memory collection so we fully re-seed it
        product.getProductIngredients().clear();

        int linked = 0;
        Set<String> seenKeysThisSnapshot = new HashSet<>();

        for (ProductDetailDto.IngredientDto ing : dtoIngredients) {
            if (ing == null) continue;

            String displayName = firstNonBlank(ing.original(), ing.canonical(), ing.id());
            if (displayName == null) continue;

            String rawKeyCandidate = firstNonBlank(ing.canonical(), ing.original(), ing.id());
            if (rawKeyCandidate == null) continue;

            String canonicalKey = rawKeyCandidate.trim().toLowerCase(Locale.ROOT);

            // avoid duplicates within a single snapshot
            if (!seenKeysThisSnapshot.add(canonicalKey)) {
                continue;
            }

            Ingredient ingredient = resolveOrCreateIngredient(canonicalKey, displayName, ean14);

            if (ingredient == null || ingredient.getId() == null) {
                log.warn("saveSnapshot: ingredient resolution failed for canonicalKey='{}' — skipping link", canonicalKey);
                continue;
            }

            ProductIngredientEntity link = new ProductIngredientEntity();
            link.setProduct(product);
            link.setIngredient(ingredient);
            link.setDisplayName(displayName);

            // Persist the link
            link = productIngredientRepo.save(link);

            // VERY IMPORTANT: update the owning side's collection in-memory
            product.getProductIngredients().add(link);

            linked++;
        }

        log.info("saveSnapshot: linked {} NEW ingredients → gtin={}", linked, ean14);

        // Flush so scoring adapter sees fresh rows AND collection
        em.flush();
        log.debug("saveSnapshot: JPA flush complete before product scoring for gtin={}", ean14);

        // ───────────── PRODUCT SCORE (PERSISTED) ─────────────
        try {
            var scoreResult = productScoringAdapter.scoreProduct(product);
            log.info("saveSnapshot: product score computed gtin={} result={}", ean14, scoreResult);
        } catch (Exception ex) {
            log.warn("saveSnapshot: product scoring FAILED for gtin={} err={}", ean14, ex.toString());
        }
    }

    // ✅ Define what "supported" means (MVP = CLEANING only)
    private boolean isSupportedDomain(ProductDomain domain) {
        if (domain == null) return false;
        // Avoid hard dependency on enum constants beyond name()
        return "CLEANING".equalsIgnoreCase(domain.name());
    }

    // ───────────── Ingredient resolution ─────────────

    private Ingredient resolveOrCreateIngredient(String canonicalKey,
                                                 String displayName,
                                                 String productEan) {
        String needle = canonicalKey;

        // 1) Exact canonical_key match
        Ingredient ingredient = ingredientRepo
                .findByCanonicalKeyIgnoreCase(canonicalKey)
                .orElse(null);

        // 2) Alias/name-based resolution via custom normalized search
        if (ingredient == null) {
            List<Ingredient> candidates = ingredientRepo.findByAllNormalized(needle);
            if (!candidates.isEmpty()) {
                ingredient = candidates.get(0);
                log.info(
                        "saveSnapshot: matched ingredient via alias/name needle='{}' → id={} canonicalKey='{}'",
                        needle,
                        ingredient.getId(),
                        safe(ingredient.getCanonicalKey())
                );
            }
        }

        // 3) Skeleton ingredient
        if (ingredient == null) {
            Ingredient created = new Ingredient();
            created.setCanonicalKey(canonicalKey);
            created.setDisplayName(displayName);
            created.setActive(true);

            ingredient = ingredientRepo.save(created);
            log.info(
                    "saveSnapshot: created skeleton ingredient id={} canonicalKey='{}' displayName='{}'",
                    ingredient.getId(), canonicalKey, safe(displayName)
            );

            // 🔔 Auto-report missing ingredient ONCE per (ingredient, product)
            try {
                missingIngredientReportService.reportWithStatus(
                        displayName,                 // ingredient_name
                        productEan,                  // product_ean
                        "backend-ingestion",         // app_version (synthetic)
                        "backend",                   // platform
                        "Skeleton ingredient auto-created from external catalog (canonicalKey=" + canonicalKey + ")"
                );
            } catch (Exception ex) {
                // Never break ingestion because reporting fails
                log.warn("Failed to record missing ingredient report for '{}' (ean={}): {}",
                        displayName, productEan, ex.getMessage(), ex);
            }
        }

        return ingredient;
    }

    // ───────────── S3 MIRRORING ─────────────

    private String mirrorExternalImageToS3(String ean14, String externalUrl) throws Exception {
        log.info("mirrorExternalImageToS3: BEGIN gtin={} url={}", ean14, externalUrl);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(externalUrl))
                .GET()
                .build();

        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        int status = resp.statusCode();

        log.info("mirrorExternalImageToS3: HTTP {} from {}", status, externalUrl);
        if (status < 200 || status >= 300) {
            throw new IllegalStateException("HTTP " + status + " from image host");
        }

        byte[] bytes = resp.body();
        int byteCount = (bytes == null) ? 0 : bytes.length;

        log.info("mirrorExternalImageToS3: downloaded {} bytes for gtin={}", byteCount, ean14);

        String contentType = resp.headers().firstValue("Content-Type").orElse("image/jpeg");

        String key = "catalog/" + ean14 + "/primary/" +
                DateTimeFormatter.ISO_INSTANT.format(Instant.now()) + "_" +
                UUID.randomUUID().toString().replace("-", "") + ".jpg";

        log.info("mirrorExternalImageToS3: uploading to S3 key={} contentType={} gtin={}",
                key, contentType, ean14);

        String s3Url = imageStorage.uploadImage(key, bytes, contentType);

        log.info("mirrorExternalImageToS3: COMPLETE → s3Url={}", s3Url);
        return s3Url;
    }

    // ───────────── HELPERS ─────────────

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
}
