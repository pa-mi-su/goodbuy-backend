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
 *
 *  - domain: our coarse category ("cleaning", "baby", "food", "unknown", …)
 *  - categorySupported:
 *        true  → we try to rate the product (subject to coverage rules)
 *        false → we do NOT rate; client should show "not rated yet" UX
 *
 *  - safetyScore / ratingLetter at PRODUCT level:
 *        derived from ingredients, but ONLY when we have full coverage.
 *        If even one ingredient is missing/unrated → product is "NR".
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
        BigDecimal safetyScore,   // product-level score (null if NR)
        String ratingLetter       // product-level letter: A–F or "NR"
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

        String primaryImageUrl = imageUrls.isEmpty() ? null : imageUrls.get(0);

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
                        if (label == null || label.isBlank()) {
                            return null;
                        }
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
                        if (label == null || label.isBlank()) {
                            return null;
                        }

                        // Prefer canonical key for search if present, else fallback to label
                        String searchKey = (i.canonical() != null && !i.canonical().isBlank())
                                ? i.canonical().trim()
                                : label;

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
                        } else {
                            // Not in DB yet → white leaf
                            return new ProductIngredientView(
                                    label,
                                    null,
                                    false,
                                    null,
                                    null
                            );
                        }
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .toList();
        }

        // ── Product-level scoring with FULL COVERAGE requirement ──────────
        BigDecimal productScore = null;
        String productRating = null;

        if (categorySupported) {
            long totalIngredients = ingredientViews.size();
            long ratedIngredients = ingredientViews.stream()
                    .filter(ProductIngredientView::inCatalog)
                    .filter(iv -> iv.safetyScore() != null)
                    .count();

            boolean fullCoverage = totalIngredients > 0 && ratedIngredients == totalIngredients;

            if (fullCoverage) {
                // Worst (lowest) ingredient score drives product score.
                for (ProductIngredientView iv : ingredientViews) {
                    if (!iv.inCatalog()) continue;
                    BigDecimal s = iv.safetyScore();
                    if (s == null) continue;

                    if (productScore == null || s.compareTo(productScore) < 0) {
                        productScore = s;
                        productRating = iv.ratingLetter();
                    }
                }
            } else if (totalIngredients > 0) {
                // We know some ingredients, but NOT all → product is NR.
                productScore = null;
                productRating = "NR";
            } else {
                // No ingredients at all → NR.
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
                List.of(),   // claims placeholder
                List.of(),   // hazards placeholder
                source,
                productScore,
                productRating
        );
    }

    private static String resolveLabel(ProductDetailDto.IngredientDto i) {
        if (i.original() != null && !i.original().isBlank()) {
            return i.original().trim();
        } else if (i.canonical() != null && !i.canonical().isBlank()) {
            return i.canonical().trim();
        } else if (i.id() != null && !i.id().isBlank()) {
            return i.id().trim();
        }
        return null;
    }
}
