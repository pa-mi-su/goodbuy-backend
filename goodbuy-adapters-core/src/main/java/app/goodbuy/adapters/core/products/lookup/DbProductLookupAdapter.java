package app.goodbuy.adapters.core.products.lookup;

import app.goodbuy.adapters.core.products.mapper.ProductDetailDtoMapper;
import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.model.ProductIngredientEntity;
import app.goodbuy.adapters.core.products.repo.ProductIngredientRepository;
import app.goodbuy.adapters.core.products.repo.ProductRepository;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductLookupPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Component
public class DbProductLookupAdapter implements ProductLookupPort {

    private static final Logger log = LoggerFactory.getLogger(DbProductLookupAdapter.class);

    private final ProductRepository productRepo;
    private final ProductIngredientRepository productIngredientRepo;
    private final ProductDetailDtoMapper mapper;

    public DbProductLookupAdapter(ProductRepository productRepo,
                                  ProductIngredientRepository productIngredientRepo,
                                  ProductDetailDtoMapper mapper) {
        this.productRepo = productRepo;
        this.productIngredientRepo = productIngredientRepo;
        this.mapper = mapper;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDetailDto> findByGtin(String gtinOrEan) {
        String ean14 = normalizeToGtin14(gtinOrEan);
        if (ean14 == null) {
            log.debug("DbProductLookupAdapter.findByGtin called with invalid gtin='{}' — returning empty", gtinOrEan);
            return Optional.empty();
        }

        log.debug("DbProductLookupAdapter.findByGtin: gtin/ean14={}", ean14);

        Optional<ProductEntity> optProduct = productRepo.findByEan(ean14);
        if (optProduct.isEmpty()) {
            log.debug("DbProductLookupAdapter.findByGtin: no product row for ean={}", ean14);
            return Optional.empty();
        }

        ProductEntity product = optProduct.get();
        log.info("DbProductLookupAdapter.findByGtin: product hit id={} ean={} name={} brand={}",
                product.getId(), product.getEan(), safe(product.getName()), safe(product.getBrand()));

        // ✅ CRITICAL FIX:
        // Fetch-join ingredient so mapping always has fully initialized Ingredient rows.
        List<ProductIngredientEntity> links = productIngredientRepo.findByProductWithIngredient(product);
        product.setProductIngredients(links);

        log.info("DbProductLookupAdapter.findByGtin: productIngredientsCount={} (with ingredient fetched) for productId={}",
                links.size(), product.getId());

        ProductDetailDto dto = mapper.toDto(product);
        log.info("DbProductLookupAdapter.findByGtin: mapped dto.ingredientsCount={} for ean={}",
                dto.ingredients() != null ? dto.ingredients().size() : 0);

        return Optional.of(dto);
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

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
