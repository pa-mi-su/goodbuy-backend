package app.goodbuy.adapters.core.products.snapshot;

import app.goodbuy.adapters.core.ingredients.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.model.ProductIngredientEntity;
import app.goodbuy.adapters.core.products.repo.ProductIngredientRepository;
import app.goodbuy.adapters.core.products.repo.ProductRepository;
import app.goodbuy.core.products.dto.ProductDetailDto;
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
import java.util.Optional;
import java.util.Set;

@Component
public class DbProductSnapshotAdapter implements ProductSnapshotPort {

    private static final Logger log = LoggerFactory.getLogger(DbProductSnapshotAdapter.class);

    private final ProductRepository productRepo;
    private final ProductIngredientRepository productIngredientRepo;
    private final IngredientRepository ingredientRepo;
    private final ProductImageStoragePort imageStorage;   // S3-backed storage

    private final HttpClient httpClient = HttpClient.newHttpClient();

    public DbProductSnapshotAdapter(
            ProductRepository productRepo,
            ProductIngredientRepository productIngredientRepo,
            IngredientRepository ingredientRepo,
            ProductImageStoragePort imageStorage
    ) {
        this.productRepo = productRepo;
        this.productIngredientRepo = productIngredientRepo;
        this.ingredientRepo = ingredientRepo;
        this.imageStorage = imageStorage;
    }

    @Override
    @Transactional
    public void saveSnapshot(ProductDetailDto dto) {
        if (dto == null) {
            log.debug("DbProductSnapshotAdapter.saveSnapshot called with null dto — ignoring");
            return;
        }

        // 🔹 Normalize GTIN to 14 digits so it matches controller / lookup.
        String rawGtin = dto.gtin();
        String ean14 = normalizeToGtin14(rawGtin);
        if (ean14 == null) {
            log.debug("DbProductSnapshotAdapter.saveSnapshot: invalid/missing gtin='{}' — ignoring", rawGtin);
            return;
        }

        log.info("DbProductSnapshotAdapter.saveSnapshot: gtin={} name={} brand={}",
                ean14, safe(dto.name()), safe(dto.brand()));

        // 1) Upsert product row in `products`
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

        // Pick a primary image URL from the DTO (external URL)
        String primaryExternalUrl = null;
        List<ProductDetailDto.ImageDto> images = dto.images();
        if (images != null) {
            primaryExternalUrl = images.stream()
                    .filter(i -> i != null && i.url() != null && !i.url().isBlank())
                    .map(ProductDetailDto.ImageDto::url)
                    .findFirst()
                    .orElse(null);
        }

        product.setPrimaryImageUrl(primaryExternalUrl);

        // 🔹 Mirror primary image to S3 if present
        if (primaryExternalUrl != null && !primaryExternalUrl.isBlank()) {
            try {
                String s3Url = mirrorExternalImageToS3(ean14, primaryExternalUrl.trim());
                product.setPrimaryImageS3Url(s3Url);
                log.info("DbProductSnapshotAdapter: set primary_image_s3_url for {} → {}", ean14, s3Url);
            } catch (Exception ex) {
                // Non-fatal: we still persist product + primary_image_url
                log.warn("DbProductSnapshotAdapter: failed to mirror external image for ean={} url={} err={}",
                        ean14, primaryExternalUrl, ex.toString());
            }
        }

        product = productRepo.save(product);
        log.info("DbProductSnapshotAdapter.saveSnapshot: product persisted id={} ean={} (isNew={})",
                product.getId(), product.getEan(), isNew);

        // 2) Rebuild product_ingredients for this product
        productIngredientRepo.deleteByProduct(product);

        List<ProductDetailDto.IngredientDto> dtoIngredients = dto.ingredients();
        if (dtoIngredients == null || dtoIngredients.isEmpty()) {
            log.info("DbProductSnapshotAdapter.saveSnapshot: no ingredients in DTO for ean={}, done", ean14);
            return;
        }

        int linkedCount = 0;
        // 🔹 Deduplicate by ingredient.id within this snapshot
        Set<Long> linkedIngredientIds = new HashSet<>();

        for (ProductDetailDto.IngredientDto ingDto : dtoIngredients) {
            if (ingDto == null) continue;

            String displayName = firstNonBlank(
                    ingDto.original(),
                    ingDto.canonical(),
                    ingDto.id()
            );
            if (displayName == null) continue;

            String canonicalKeyCandidate = firstNonBlank(
                    ingDto.canonical(),
                    ingDto.original(),
                    ingDto.id()
            );
            if (canonicalKeyCandidate == null) continue;

            String canonicalKey = canonicalKeyCandidate.trim().toLowerCase(Locale.ROOT);

            Optional<Ingredient> optIngredient =
                    ingredientRepo.findByCanonicalKeyIgnoreCase(canonicalKey);

            if (optIngredient.isEmpty()) {
                log.debug("DbProductSnapshotAdapter.saveSnapshot: no Ingredient for canonicalKey='{}', skipping",
                        canonicalKey);
                continue;
            }

            Ingredient ingredient = optIngredient.get();
            Long ingredientId = ingredient.getId();
            if (ingredientId == null) {
                continue;
            }

            // In-snapshot dedupe
            if (!linkedIngredientIds.add(ingredientId)) {
                log.debug("DbProductSnapshotAdapter.saveSnapshot: ingredient_id={} already linked in this snapshot for product_id={}, skipping dup",
                        ingredientId, product.getId());
                continue;
            }

            // 🔒 Hard guard against uq_prod_ing_pair — if row already exists in DB, skip insert.
            if (productIngredientRepo.existsByProductAndIngredient(product, ingredient)) {
                log.debug("DbProductSnapshotAdapter.saveSnapshot: link already exists product_id={} ingredient_id={}, skipping DB dup",
                        product.getId(), ingredientId);
                continue;
            }

            ProductIngredientEntity link = new ProductIngredientEntity();
            link.setProduct(product);
            link.setIngredient(ingredient);
            link.setDisplayName(displayName);

            productIngredientRepo.save(link);
            linkedCount++;
        }

        log.info("DbProductSnapshotAdapter.saveSnapshot: linked {} ingredient(s) to product {}",
                linkedCount, ean14);
    }

    // ───────────────────────────────────────────────────────────────────────────
    // External image → S3 mirror
    // ───────────────────────────────────────────────────────────────────────────

    private String mirrorExternalImageToS3(String ean14, String externalUrl) throws Exception {
        log.info("DbProductSnapshotAdapter: downloading external image ean={} url={}", ean14, externalUrl);

        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(externalUrl))
                .GET()
                .build();

        HttpResponse<byte[]> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
            throw new IllegalStateException("HTTP " + resp.statusCode() + " when fetching image");
        }

        byte[] bytes = resp.body();
        String contentType = resp.headers()
                .firstValue("Content-Type")
                .orElse("image/jpeg");

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String key = "catalog/"
                + ean14 + "/primary/"
                + timestamp + "_"
                + java.util.UUID.randomUUID().toString().replace("-", "")
                + ".jpg";

        // ProductImageStoragePort is S3-backed (S3StorageService)
        return imageStorage.uploadImage(key, bytes, contentType);
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────────────

    private static String normalizeToGtin14(String raw) {
        if (raw == null) return null;
        String digits = raw.trim();
        if (!digits.matches("\\d+")) {
            return null;
        }
        return switch (digits.length()) {
            case 14 -> digits;
            case 13 -> "0" + digits;
            case 12 -> "00" + digits;
            default -> null;
        };
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String v : values) {
            if (v != null && !v.isBlank()) {
                return v.trim();
            }
        }
        return null;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
