package app.goodbuy.adapters.core.products.mapper;

import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.model.ProductIngredientEntity;
import app.goodbuy.core.products.dto.ProductDetailDto;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class ProductDetailDtoMapper {

    public ProductDetailDto toDto(ProductEntity product) {
        // ----- Images -----
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

        // ----- Ingredients -----
        List<ProductDetailDto.IngredientDto> ingredientDtos =
                (product.getProductIngredients() == null)
                        ? List.of()
                        : product.getProductIngredients().stream()
                        .filter(Objects::nonNull)
                        .map(this::mapIngredientLink)
                        .filter(Objects::nonNull)
                        .toList();

        // Titles / manufacturer not modeled in DB yet
        Map<String, String> titles = Collections.emptyMap();
        Map<String, String> manufacturer = Collections.emptyMap();

        // Domain is stored as text on the product entity (e.g. "cleaning", "baby")
        String domain = safe(product.getDomain());
        if ("-".equals(domain)) {
            domain = "unknown";
        }

        // ✅ Pass through stored product-level score + letter from DB
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
                "GOODBUY-DB",
                domain,
                product.getSafetyScore(),   // <-- 64
                product.getRatingLetter()   // <-- "D"
        );
    }

    private ProductDetailDto.IngredientDto mapIngredientLink(ProductIngredientEntity link) {
        if (link.getIngredient() == null) {
            // Should not happen, but be defensive
            String label = firstNonBlank(link.getDisplayName());
            if (label == null) {
                return null;
            }
            return new ProductDetailDto.IngredientDto(
                    null,          // id
                    label,         // original
                    null,          // canonical
                    Collections.emptyMap(),
                    null,
                    null
            );
        }

        var ingredient = link.getIngredient();

        String piDisplayName  = link.getDisplayName();
        String canonicalKey   = ingredient.getCanonicalKey();
        String ingredientName = ingredient.getDisplayName();

        // original = what we showed on label for this product
        String original = firstNonBlank(piDisplayName, ingredientName, canonicalKey);
        // canonical = GoodBuy’s normalized name
        String canonical = firstNonBlank(ingredientName, canonicalKey);

        // Our internal stable ID = canonical_key
        String id = canonicalKey;

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
