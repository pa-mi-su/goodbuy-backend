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

        Map<String, String> titles = Collections.emptyMap();
        Map<String, String> manufacturer = Collections.emptyMap();

        String domain = safe(product.getDomain());
        if ("-".equals(domain)) {
            domain = "unknown";
        }

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
                product.getSafetyScore(),
                product.getRatingLetter()
        );
    }

    private ProductDetailDto.IngredientDto mapIngredientLink(ProductIngredientEntity link) {
        if (link == null) return null;

        // Always show something to the client
        String label = firstNonBlank(link.getDisplayName());

        if (link.getIngredient() == null) {
            if (label == null) return null;

            return new ProductDetailDto.IngredientDto(
                    null, // id
                    label, // original
                    null, // canonical
                    Collections.emptyMap(),
                    null,
                    null
            );
        }

        var ingredient = link.getIngredient();

        String canonicalKey = firstNonBlank(ingredient.getCanonicalKey());
        String ingredientName = firstNonBlank(ingredient.getDisplayName());

        // original = what came from this product label/snapshot
        String original = firstNonBlank(label, ingredientName, canonicalKey);

        // ✅ canonical MUST be canonical_key (stable key), not display_name
        String canonical = canonicalKey;

        // ✅ id also stays canonical_key (stable)
        String id = canonicalKey;

        return new ProductDetailDto.IngredientDto(
                id,
                original,
                canonical,
                Collections.emptyMap(),
                null,
                null
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
