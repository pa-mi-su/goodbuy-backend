package app.goodbuy.adapters.core.products.lookup;

import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.repo.ProductRepository;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductLookupPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Component
public class DbProductLookupAdapter implements ProductLookupPort {

    private static final Logger log = LoggerFactory.getLogger(DbProductLookupAdapter.class);

    private final ProductRepository productRepo;
    private final JdbcTemplate jdbcTemplate;

    public DbProductLookupAdapter(ProductRepository productRepo,
                                  JdbcTemplate jdbcTemplate) {
        this.productRepo = productRepo;
        this.jdbcTemplate = jdbcTemplate;
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

        // 1) Look up product in GoodBuy products table
        Optional<ProductEntity> optProduct = productRepo.findByEan(ean14);
        if (optProduct.isEmpty()) {
            log.debug("DbProductLookupAdapter.findByGtin: no product row for ean={}", ean14);
            return Optional.empty();
        }

        ProductEntity product = optProduct.get();
        log.info("DbProductLookupAdapter.findByGtin: product hit id={} ean={} name={} brand={}",
                product.getId(), product.getEan(), safe(product.getName()), safe(product.getBrand()));

        // 2) Load linked ingredients via product_ingredients + ingredients
        List<ProductDetailDto.IngredientDto> ingredientDtos = loadIngredientDtosForProduct(product.getId());
        log.info("DbProductLookupAdapter.findByGtin: loaded {} ingredient link(s) for product_id={}",
                ingredientDtos.size(), product.getId());

        // 3) Map to ProductDetailDto (GoodBuy-only snapshot)
        ProductDetailDto dto = toDto(product, ingredientDtos);

        return Optional.of(dto);
    }

    // ───────────────────────────────────────────────────────────────────────────
    // INGREDIENT LOADING
    // ───────────────────────────────────────────────────────────────────────────

    private List<ProductDetailDto.IngredientDto> loadIngredientDtosForProduct(Long productId) {
        String sql = """
                SELECT
                    pi.display_name          AS pi_display_name,
                    i.canonical_key          AS canonical_key,
                    i.display_name           AS ingredient_display_name
                FROM product_ingredients pi
                JOIN ingredients i ON i.id = pi.ingredient_id
                WHERE pi.product_id = ?
                ORDER BY pi.id
                """;

        return jdbcTemplate.query(sql, (rs, rowNum) -> mapRowToIngredientDto(rs), productId);
    }

    private ProductDetailDto.IngredientDto mapRowToIngredientDto(ResultSet rs) throws SQLException {
        String piDisplayName   = rs.getString("pi_display_name");
        String canonicalKey    = rs.getString("canonical_key");
        String ingredientName  = rs.getString("ingredient_display_name");

        // original = what we showed on label for this product (best-effort)
        String original = firstNonBlank(piDisplayName, ingredientName, canonicalKey);
        // canonical = GoodBuy’s normalized name
        String canonical = firstNonBlank(ingredientName, canonicalKey);

        // GoodBuy ingredient ID = canonical_key (stable internal key)
        String id = canonicalKey;

        // No external IDs / vegan flags from DB lookup path (handled elsewhere if needed)
        Map<String, String> externalIds = Collections.emptyMap();
        Boolean isVegan = null;
        Boolean isVegetarian = null;

        return new ProductDetailDto.IngredientDto(
                id,
                original,
                canonical,
                externalIds,
                isVegan,
                isVegetarian
        );
    }

    // ───────────────────────────────────────────────────────────────────────────
    // MAIN DTO MAPPER
    // ───────────────────────────────────────────────────────────────────────────

    private ProductDetailDto toDto(ProductEntity product,
                                   List<ProductDetailDto.IngredientDto> ingredientDtos) {

        // Prefer our S3-hosted image; fall back to legacy URL if needed
        String imageUrl = firstNonBlank(
                product.getPrimaryImageS3Url(),
                product.getPrimaryImageUrl()
        );

        List<ProductDetailDto.ImageDto> images = Collections.emptyList();
        if (imageUrl != null) {
            images = List.of(new ProductDetailDto.ImageDto(
                    imageUrl,
                    null,
                    null
            ));
        }

        // titles / manufacturer not modeled in DB yet
        Map<String, String> titles = Collections.emptyMap();
        Map<String, String> manufacturer = Collections.emptyMap();

        return new ProductDetailDto(
                product.getEan(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getDescription(),
                images,
                ingredientDtos,
                titles,
                manufacturer,
                "GOODBUY-DB"   // clearly mark this as our own DB snapshot
        );
    }

    // ───────────────────────────────────────────────────────────────────────────
    // HELPERS
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
