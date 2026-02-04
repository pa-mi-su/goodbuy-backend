package app.goodbuy.products.view;

import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.ingredients.IngredientReadService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * High-level product view for iOS ResultView.
 */
public record ProductView(
        String gtin,
        String name,
        String brand,
        String category,
        String domain,
        boolean categorySupported,
        String primaryImageUrl,
        List<String> images,
        List<ProductIngredientView> ingredients,
        List<String> claims,
        List<String> hazards,
        String source,
        BigDecimal safetyScore,
        String ratingLetter
) {

    public static ProductView of(ProductDetailDto dto,
                                 String source,
                                 IngredientReadService ingredientReadService,
                                 boolean categorySupported,
                                 String domainOverride) {

        // Effective domain: controller override → DTO → "unknown"
        String effectiveDomain;
        if (domainOverride != null && !domainOverride.isBlank()) {
            effectiveDomain = domainOverride.trim();
        } else if (dto.domain() != null && !dto.domain().isBlank()) {
            effectiveDomain = dto.domain().trim();
        } else {
            effectiveDomain = "unknown";
        }

        // Images → simple list of URLs
        List<String> imageUrls = (dto.images() == null) ? List.of() :
                dto.images().stream()
                        .map(ProductDetailDto.ImageDto::url)
                        .filter(Objects::nonNull)
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .distinct()
                        .toList();

        String primaryImageUrl = resolvePrimaryImageUrl(imageUrls);

        // Build ingredient views
        List<ProductIngredientView> ingredientViews;

        if (dto.ingredients() == null) {
            ingredientViews = List.of();

        } else if (!categorySupported) {
            // Unsupported domain → labels only, no GoodBuy rating
            ingredientViews = dto.ingredients().stream()
                    .filter(Objects::nonNull)
                    .map(i -> {
                        String label = resolveLabel(i);
                        if (label == null || label.isBlank()) return null;

                        return new ProductIngredientView(
                                label,
                                null,
                                false,
                                null,
                                null
                        );
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();

        } else {
            // Supported domain → resolve & rate via GoodBuy DB
            ingredientViews = dto.ingredients().stream()
                    .filter(Objects::nonNull)
                    .map(i -> {
                        String label = resolveLabel(i);
                        if (label == null || label.isBlank()) return null;

                        // ✅ CRITICAL: Search by stable canonical key first
                        // DTO.id should be canonical_key (stable).
                        String searchKey = firstNonBlank(
                                i.id(),        // <-- BEST
                                i.canonical(),  // <-- next
                                label          // <-- last resort
                        );

                        Optional<IngredientDTO> opt = ingredientReadService.searchRanked(searchKey);
                        if (opt.isPresent()) {
                            IngredientDTO ing = opt.get();
                            return new ProductIngredientView(
                                    label,
                                    ing.canonicalKey(),
                                    true,
                                    ing.ratingLetter(),
                                    ing.safetyScore()
                            );
                        }

                        // Not in DB yet → white leaf
                        return new ProductIngredientView(
                                label,
                                null,
                                false,
                                null,
                                null
                        );
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }

        // ── Product-level scoring ─────────────────────────────────────────────
        BigDecimal productScore = dto.safetyScore();
        String productRating    = dto.ratingLetter();

        if (productScore == null && (productRating == null || productRating.isBlank()) && categorySupported) {
            long totalIngredients = ingredientViews.size();
            long ratedIngredients = ingredientViews.stream()
                    .filter(ProductIngredientView::inCatalog)
                    .filter(iv -> iv.safetyScore() != null)
                    .count();

            boolean fullCoverage = totalIngredients > 0 && ratedIngredients == totalIngredients;

            if (fullCoverage) {
                for (ProductIngredientView iv : ingredientViews) {
                    if (!iv.inCatalog()) continue;
                    BigDecimal s = iv.safetyScore();
                    if (s == null) continue;

                    if (productScore == null || s.compareTo(productScore) < 0) {
                        productScore = s;
                        productRating = iv.ratingLetter();
                    }
                }
                if (productRating == null || productRating.isBlank()) {
                    productRating = "NR";
                }
            } else {
                productScore = null;
                productRating = "NR";
            }
        }

        return new ProductView(
                dto.gtin(),
                dto.name(),
                dto.brand(),
                dto.category(),
                effectiveDomain,
                categorySupported,
                primaryImageUrl,
                imageUrls,
                ingredientViews,
                List.of(),
                List.of(),
                source,
                productScore,
                productRating
        );
    }

    private static String resolveLabel(ProductDetailDto.IngredientDto i) {
        if (i.original() != null && !i.original().isBlank()) return i.original().trim();
        if (i.canonical() != null && !i.canonical().isBlank()) return i.canonical().trim();
        if (i.id() != null && !i.id().isBlank()) return i.id().trim();
        return null;
    }

    private static String firstNonBlank(String... vals) {
        if (vals == null) return null;
        for (String v : vals) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    private static String resolvePrimaryImageUrl(List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) return null;

        for (String url : imageUrls) {
            if (url != null && url.contains(".s3.amazonaws.com")) {
                return url;
            }
        }
        return imageUrls.get(0);
    }
}
