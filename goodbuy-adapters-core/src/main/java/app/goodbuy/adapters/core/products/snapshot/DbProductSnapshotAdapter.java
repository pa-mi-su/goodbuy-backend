package app.goodbuy.adapters.core.products.snapshot;

import app.goodbuy.adapters.core.ingredients.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.model.ProductIngredientEntity;
import app.goodbuy.adapters.core.products.repo.ProductIngredientRepository;
import app.goodbuy.adapters.core.products.repo.ProductRepository;
import app.goodbuy.core.products.domain.ProductDomain;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductDomainResolverPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import app.goodbuy.core.storage.ProductImageStoragePort;
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

    // ✅ Use the port, not a concrete classifier
    private final ProductDomainResolverPort domainResolver;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DbProductSnapshotAdapter(
            ProductRepository productRepo,
            ProductIngredientRepository productIngredientRepo,
            IngredientRepository ingredientRepo,
            ProductImageStoragePort imageStorage,
            ProductDomainResolverPort domainResolver
    ) {
        this.productRepo = productRepo;
        this.productIngredientRepo = productIngredientRepo;
        this.ingredientRepo = ingredientRepo;
        this.imageStorage = imageStorage;
        this.domainResolver = domainResolver;
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

        // ----------- PRODUCT UPSERT -----------
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

        // ----------- DOMAIN CLASSIFICATION (PERSISTED) -----------
        // Use existing DB domain as a hint (so we don't downgrade a known CLEANING, etc.).
        String existingDomain = product.getDomain(); // may be "unknown" or null on brand new rows

        ProductDomain domainEnum = domainResolver.classify(
                existingDomain,
                product.getCategory(),
                product.getName(),
                product.getBrand()
        );

        // Persist as lowercase string ("cleaning", "baby", "food", "unknown", ...)
        String domainStr = domainEnum.name().toLowerCase(Locale.ROOT);
        product.setDomain(domainStr);

        log.info("saveSnapshot: classified domain={} for gtin={}", domainStr, ean14);

        // -------- IMAGE SELECTION LOGGING --------
        List<ProductDetailDto.ImageDto> images = dto.images();
        int imageCount = images == null ? 0 : images.size();

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

        // ----------- S3 MIRROR -----------
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

        // ----------- SAVE PRODUCT --------
        product = productRepo.save(product);
        log.info("saveSnapshot: product persisted id={} ean={} (isNew={}) domain={} s3Url={}",
                product.getId(), product.getEan(), isNew, product.getDomain(), safe(product.getPrimaryImageS3Url()));

        // ----------- INGREDIENT LINKS -----------
        productIngredientRepo.deleteByProduct(product);

        List<ProductDetailDto.IngredientDto> dtoIngredients = dto.ingredients();
        if (dtoIngredients == null || dtoIngredients.isEmpty()) {
            log.info("saveSnapshot: no ingredients for gtin={} — DONE", ean14);
            return;
        }

        int linked = 0;
        Set<Long> seen = new HashSet<>();

        for (ProductDetailDto.IngredientDto ing : dtoIngredients) {
            if (ing == null) continue;

            // What we show to users (best human-facing label we have)
            String displayName = firstNonBlank(
                    ing.original(), ing.canonical(), ing.id());
            if (displayName == null) continue;

            // What we use as the canonical_key / normalized needle
            String rawKeyCandidate = firstNonBlank(
                    ing.canonical(), ing.original(), ing.id());
            if (rawKeyCandidate == null) continue;

            String canonicalKey = rawKeyCandidate.trim().toLowerCase(Locale.ROOT);
            String needle = canonicalKey; // already lowercased

            // --- Try to resolve an existing Ingredient before creating a new one ---

            Ingredient ingredient = null;

            // 1) Exact canonical_key match (case-insensitive)
            ingredient = ingredientRepo
                    .findByCanonicalKeyIgnoreCase(canonicalKey)
                    .orElse(null);

            // 2) If not found, try alias/name-based resolution
            if (ingredient == null) {
                List<Ingredient> candidates = ingredientRepo.findByAllNormalized(needle);
                if (!candidates.isEmpty()) {
                    ingredient = candidates.get(0); // first is fine; we de-dup via IDs anyway
                    log.info(
                            "saveSnapshot: matched ingredient via alias/name needle='{}' → id={} canonicalKey='{}'",
                            needle,
                            ingredient.getId(),
                            safe(ingredient.getCanonicalKey())
                    );
                }
            }

            // 3) If still not found, create a skeleton ingredient
            if (ingredient == null) {
                Ingredient created = new Ingredient();
                created.setCanonicalKey(canonicalKey);
                created.setDisplayName(displayName);
                // Start active; ratingLetter/safetyScore/etc. remain null.
                created.setActive(true);

                ingredient = ingredientRepo.save(created);
                log.info(
                        "saveSnapshot: created skeleton ingredient id={} canonicalKey='{}' displayName='{}'",
                        ingredient.getId(), canonicalKey, safe(displayName)
                );
            }

            Long id = ingredient.getId();
            if (id == null) {
                log.warn("saveSnapshot: ingredient entity has null id for canonicalKey='{}' — skipping link", canonicalKey);
                continue;
            }

            if (!seen.add(id)) {
                // already linked in this snapshot loop
                continue;
            }

            ProductIngredientEntity link = new ProductIngredientEntity();
            link.setProduct(product);
            link.setIngredient(ingredient);
            link.setDisplayName(displayName);

            productIngredientRepo.save(link);
            linked++;
        }

        log.info("saveSnapshot: linked {} ingredients → gtin={}", linked, ean14);
    }

    // ----------- S3 MIRRORING -----------
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
        int byteCount = bytes == null ? 0 : bytes.length;

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

    // ----------- HELPERS -----------

    private static String normalizeToGtin14(String raw) {
        if (raw == null) return null;
        if (!raw.trim().matches("\\d+")) return null;
        return switch (raw.trim().length()) {
            case 14 -> raw.trim();
            case 13 -> "0" + raw.trim();
            case 12 -> "00" + raw.trim();
            default -> null;
        };
    }

    private static String firstNonBlank(String... vals) {
        for (String v : vals) if (v != null && !v.isBlank()) return v.trim();
        return null;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "(null)" : s;
    }
}
