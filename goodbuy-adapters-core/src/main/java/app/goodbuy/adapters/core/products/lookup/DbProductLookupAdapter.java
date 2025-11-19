package app.goodbuy.adapters.core.products.lookup;

import app.goodbuy.adapters.core.ingredients.IngredientRepository;
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
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class DbProductLookupAdapter implements ProductLookupPort {

    private static final Logger log = LoggerFactory.getLogger(DbProductLookupAdapter.class);

    private final ProductRepository productRepo;
    private final IngredientRepository ingredientRepo;
    private final JdbcTemplate jdbcTemplate;

    public DbProductLookupAdapter(ProductRepository productRepo,
                                  IngredientRepository ingredientRepo,
                                  JdbcTemplate jdbcTemplate) {
        this.productRepo = productRepo;
        this.ingredientRepo = ingredientRepo;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ProductDetailDto> findByGtin(String gtin14) {
        if (gtin14 == null || gtin14.isBlank()) {
            log.debug("DbProductLookupAdapter.findByGtin called with null/blank gtin — returning empty");
            return Optional.empty();
        }

        final String ean = gtin14.trim();
        log.debug("DbProductLookupAdapter.findByGtin: gtin/ean={}", ean);

        // 1) Look up product in GoodBuy products table
        Optional<ProductEntity> optProduct = productRepo.findByEan(ean);
        if (optProduct.isEmpty()) {
            log.debug("DbProductLookupAdapter.findByGtin: no product row for ean={}", ean);
            return Optional.empty();
        }

        ProductEntity product = optProduct.get();
        log.info("DbProductLookupAdapter.findByGtin: product hit id={} ean={} name={} brand={}",
                product.getId(), product.getEan(), safe(product.getName()), safe(product.getBrand()));

        // 2) Load linked ingredients via product_ingredients + ingredients
        List<ProductDetailDto.IngredientDto> ingredientDtos = loadIngredientDtosForProduct(product.getId());

        // 3) Map to ProductDetailDto
        ProductDetailDto dto = toDto(product, ingredientDtos);

        return Optional.of(dto);
    }

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

        // This is the name shown next to the leaf in iOS.
        String original = firstNonBlank(piDisplayName, ingredientName, canonicalKey);
        // Canonical = GoodBuy's normalized / display name.
        String canonical = firstNonBlank(ingredientName, canonicalKey);

        // Our GoodBuy ingredient ID = canonical_key (stable key)
        String id = canonicalKey;

        // No external IDs / vegan flags yet → keep null/empty for now
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

    private ProductDetailDto toDto(ProductEntity product,
                                   List<ProductDetailDto.IngredientDto> ingredientDtos) {

        // Single primary image → map into one ImageDto if present
        List<ProductDetailDto.ImageDto> images = Collections.emptyList();
        if (product.getPrimaryImageUrl() != null && !product.getPrimaryImageUrl().isBlank()) {
            images = List.of(new ProductDetailDto.ImageDto(
                    product.getPrimaryImageUrl().trim(),
                    null,
                    null
            ));
        }

        // titles / manufacturer not modeled yet → empty maps
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
                "GOODBUY-DB"   // mark this as coming from our own DB
        );
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Helpers
    // ───────────────────────────────────────────────────────────────────────────

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
