package app.goodbuy.adapters.core.products.snapshot;

import app.goodbuy.adapters.core.ingredients.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.model.ProductIngredientEntity;
import app.goodbuy.adapters.core.products.repo.ProductIngredientRepository;
import app.goodbuy.adapters.core.products.repo.ProductRepository;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class DbProductSnapshotAdapter implements ProductSnapshotPort {

    private static final Logger log = LoggerFactory.getLogger(DbProductSnapshotAdapter.class);

    private final ProductRepository productRepo;
    private final ProductIngredientRepository productIngredientRepo;
    private final IngredientRepository ingredientRepo;

    public DbProductSnapshotAdapter(
            ProductRepository productRepo,
            ProductIngredientRepository productIngredientRepo,
            IngredientRepository ingredientRepo
    ) {
        this.productRepo = productRepo;
        this.productIngredientRepo = productIngredientRepo;
        this.ingredientRepo = ingredientRepo;
    }

    @Override
    @Transactional
    public void saveSnapshot(ProductDetailDto dto) {
        if (dto == null) {
            log.debug("DbProductSnapshotAdapter.saveSnapshot called with null dto — ignoring");
            return;
        }
        if (dto.gtin() == null || dto.gtin().isBlank()) {
            log.debug("DbProductSnapshotAdapter.saveSnapshot called with missing gtin — ignoring");
            return;
        }

        final String ean = dto.gtin().trim();
        log.info("DbProductSnapshotAdapter.saveSnapshot: gtin/ean={} name={} brand={}",
                ean, safe(dto.name()), safe(dto.brand()));

        // 1) Upsert product row in `products`
        ProductEntity product = productRepo.findByEan(ean)
                .orElseGet(ProductEntity::new);

        boolean isNew = (product.getId() == null);
        if (isNew) {
            product.setEan(ean);
        }

        product.setName(dto.name());
        product.setBrand(dto.brand());
        product.setCategory(dto.category());
        product.setDescription(dto.description());

        // Pick a primary image URL if present
        String primaryImageUrl = null;
        List<ProductDetailDto.ImageDto> images = dto.images();
        if (images != null) {
            primaryImageUrl = images.stream()
                    .filter(i -> i != null && i.url() != null && !i.url().isBlank())
                    .map(ProductDetailDto.ImageDto::url)
                    .findFirst()
                    .orElse(null);
        }
        product.setPrimaryImageUrl(primaryImageUrl);

        product = productRepo.save(product);
        log.info("DbProductSnapshotAdapter.saveSnapshot: product persisted id={} ean={} (isNew={})",
                product.getId(), product.getEan(), isNew);

        // 2) Rebuild product_ingredients for this product
        productIngredientRepo.deleteByProduct(product);

        List<ProductDetailDto.IngredientDto> dtoIngredients = dto.ingredients();
        if (dtoIngredients == null || dtoIngredients.isEmpty()) {
            log.info("DbProductSnapshotAdapter.saveSnapshot: no ingredients in DTO for ean={}, done", ean);
            return;
        }

        int linkedCount = 0;
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
                log.debug("DbProductSnapshotAdapter.saveSnapshot: no Ingredient for canonicalKey='{}' (displayName='{}'), skipping link",
                        canonicalKey, displayName);
                continue;
            }

            Ingredient ingredient = optIngredient.get();

            ProductIngredientEntity link = new ProductIngredientEntity();
            link.setProduct(product);
            link.setIngredient(ingredient);
            link.setDisplayName(displayName);

            productIngredientRepo.save(link);
            linkedCount++;
        }

        log.info("DbProductSnapshotAdapter.saveSnapshot: linked {} ingredient(s) to product id={} ean={}",
                linkedCount, product.getId(), product.getEan());
    }

    // helpers

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
