package app.goodbuy.products.view;

import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.ingredients.scoring.IngredientScoreResult;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.scoring.ProductScoringEngine;
import app.goodbuy.ingredients.IngredientReadService;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * High-level product view for iOS ResultView.
 *
 * FIXES:
 * 1) Prevent false-positive “in DB” matches by using STRICT ranked search (no loose substring fallback).
 * 2) CanonicalKey is DB-truth for iOS:
 *    - If we found an ingredient in DB, we ALWAYS return canonicalKey (stable key).
 *    - inCatalog reflects “hasDetails / researched” (may be false even if row exists).
 *    - ratingLetter/safetyScore only exposed when hasDetails == true.
 */
public record ProductView(
        String gtin,
        String name,
        String brand,
        String category,
        String domain,
        boolean categorySupported,
        String scoringStatus,
        String scoringMessage,
        String primaryImageUrl,
        List<String> images,
        List<ProductIngredientView> ingredients,
        List<String> claims,
        List<String> hazards,
        List<ProductGuidanceSignalView> guidanceSignals,
        String guidanceConfidence,
        String source,
        BigDecimal safetyScore,
        String ratingLetter
) {

    private static final ProductScoringEngine PRODUCT_SCORING_ENGINE = new ProductScoringEngine();

    public static ProductView of(ProductDetailDto dto,
                                 String source,
                                 IngredientReadService ingredientReadService,
                                 boolean categorySupported,
                                 String domainOverride) {
        return of(dto, source, ingredientReadService, categorySupported, domainOverride, List.of());
    }

    public static ProductView of(ProductDetailDto dto,
                                 String source,
                                 IngredientReadService ingredientReadService,
                                 boolean categorySupported,
                                 String domainOverride,
                                 List<IngredientDTO> immediateIngredientReads) {

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
        Map<String, IngredientDTO> immediateIngredientMap = indexImmediateIngredients(immediateIngredientReads);

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

                        // Search by stable canonical key first (then canonical, then label).
                        String searchKey = firstNonBlank(
                                i.id(),
                                i.canonical(),
                                label
                        );

                        // ✅ CRITICAL FIX:
                        // Use STRICT search so “not in DB” ingredients do NOT accidentally match
                        // some other ingredient via loose substring fallback.
                        Optional<IngredientDTO> opt = ingredientReadService.searchRanked(searchKey, false);
                        if (opt.isEmpty()) {
                            opt = findImmediateIngredient(immediateIngredientMap, searchKey, label);
                        }

                        if (opt.isPresent()) {
                            IngredientDTO ing = opt.get();

                            // IMPORTANT:
                            // - canonicalKey != null means "exists in our DB" (found by canonical or alias)
                            // - inCatalog means "has real details / researched", not merely "row exists"
                            boolean hasDetails = hasDetails(ing);

                            // ✅ CRITICAL FIX:
                            // Always expose canonicalKey if the DB lookup succeeded (DB-truth key),
                            // even if it is a skeleton (hasDetails=false).
                            // iOS uses canonicalKey==nil as “not in DB”.
                            return new ProductIngredientView(
                                    label,
                                    ing.canonicalKey(),
                                    hasDetails,
                                    hasDetails ? ing.ratingLetter() : null,
                                    hasDetails ? ing.safetyScore() : null
                            );
                        }

                        // Not in DB yet → missing/unknown leaf
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

        BigDecimal productScore = dto.safetyScore();
        String productRating    = dto.ratingLetter();
        if (!hasProductScore(productScore, productRating) && categorySupported) {
            ProductScoreSnapshot provisionalScore = provisionalScore(immediateIngredientReads, effectiveDomain);
            if (provisionalScore != null) {
                productScore = provisionalScore.safetyScore();
                productRating = provisionalScore.ratingLetter();
            }
        }
        long totalIngredients = ingredientViews.size();
        long catalogIngredients = ingredientViews.stream()
                .filter(ProductIngredientView::inCatalog)
                .count();
        double coverageRatio = totalIngredients == 0 ? 0.0 : (catalogIngredients / (double) totalIngredients);
        String scoringStatus = "scored";
        String scoringMessage = null;
        boolean missingIngredientList = categorySupported && ingredientViews.isEmpty();

        boolean hasProductScore = hasProductScore(productScore, productRating);

        if (!hasProductScore) {
            if (!categorySupported) {
                scoringStatus = "out_of_domain";
            } else if (missingIngredientList) {
                scoringStatus = "missing_ingredient_list";
                scoringMessage = "This product listing does not include an ingredient list yet. We logged it for review.";
            } else if (coverageRatio < 0.6d) {
                scoringStatus = "needs_ingredient_evidence";
                scoringMessage = "We only matched " + catalogIngredients + " of " + totalIngredients
                        + " ingredients. Upload ingredient-label photos so we can seed the missing ones.";
            } else {
                scoringStatus = "pending_ingredients";
                scoringMessage = "We do not yet have enough authoritative evidence to score every ingredient in this product.";
            }
        }

        ProductGuidanceSignals.ProductGuidanceSummary guidance = ProductGuidanceSignals.build(
                effectiveDomain,
                dto.name(),
                dto.category(),
                scoringStatus,
                productScore,
                productRating,
                ingredientViews
        );

        return new ProductView(
                dto.gtin(),
                dto.name(),
                dto.brand(),
                dto.category(),
                effectiveDomain,
                categorySupported,
                scoringStatus,
                scoringMessage,
                primaryImageUrl,
                imageUrls,
                ingredientViews,
                List.of(),
                List.of(),
                guidance.signals(),
                guidance.confidence(),
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

    private static Optional<IngredientDTO> findImmediateIngredient(Map<String, IngredientDTO> indexed, String... candidates) {
        if (indexed == null || indexed.isEmpty() || candidates == null) {
            return Optional.empty();
        }

        for (String candidate : candidates) {
            String normalized = normalizeNeedle(candidate);
            if (normalized == null) {
                continue;
            }

            IngredientDTO ingredient = indexed.get(normalized);
            if (ingredient != null) {
                return Optional.of(ingredient);
            }
        }

        return Optional.empty();
    }

    private static Map<String, IngredientDTO> indexImmediateIngredients(List<IngredientDTO> ingredients) {
        if (ingredients == null || ingredients.isEmpty()) {
            return Map.of();
        }

        Map<String, IngredientDTO> indexed = new LinkedHashMap<>();
        for (IngredientDTO ingredient : ingredients) {
            if (ingredient == null) {
                continue;
            }
            putIfPresent(indexed, ingredient.canonicalKey(), ingredient);
            putIfPresent(indexed, ingredient.displayName(), ingredient);
            if (ingredient.aliases() != null) {
                ingredient.aliases().forEach(alias -> putIfPresent(indexed, alias, ingredient));
            }
        }
        return indexed;
    }

    private static void putIfPresent(Map<String, IngredientDTO> indexed, String key, IngredientDTO ingredient) {
        String normalized = normalizeNeedle(key);
        if (normalized != null && ingredient != null) {
            indexed.putIfAbsent(normalized, ingredient);
        }
    }

    private static ProductScoreSnapshot provisionalScore(List<IngredientDTO> immediateIngredientReads, String domain) {
        if (immediateIngredientReads == null || immediateIngredientReads.isEmpty()) {
            return null;
        }

        List<IngredientScoreResult> scoredIngredients = immediateIngredientReads.stream()
                .filter(Objects::nonNull)
                .filter(ingredient -> ingredient.safetyScore() != null)
                .filter(ingredient -> ingredient.ratingLetter() != null && !ingredient.ratingLetter().isBlank())
                .map(ingredient -> new IngredientScoreResult(
                        ingredient.safetyScore().intValue(),
                        ingredient.ratingLetter(),
                        List.of("Immediate ingredient read generated during scan.")
                ))
                .toList();

        if (scoredIngredients.isEmpty()) {
            return null;
        }

        var provisional = PRODUCT_SCORING_ENGINE.score(scoredIngredients, domain);
        if (provisional.ratingLetter() == null
                || provisional.ratingLetter().isBlank()
                || "NR".equalsIgnoreCase(provisional.ratingLetter())) {
            return null;
        }

        return new ProductScoreSnapshot(BigDecimal.valueOf(provisional.safetyScore()), provisional.ratingLetter());
    }

    private static boolean hasProductScore(BigDecimal productScore, String productRating) {
        return productScore != null
                && productRating != null
                && !productRating.isBlank()
                && !"NR".equalsIgnoreCase(productRating);
    }

    private static String normalizeNeedle(String raw) {
        if (raw == null) {
            return null;
        }

        String normalized = raw
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ")
                .replace('’', '\'')
                .replace('–', '-')
                .replace('—', '-');

        return normalized.isEmpty() ? null : normalized;
    }

    /**
     * Determines whether an ingredient has real detail content (researched),
     * vs. being a skeleton row created during ingestion.
     *
     * NOTE: This does NOT require a schema/DTO change.
     */
    private static boolean hasDetails(IngredientDTO ing) {
        if (ing == null) return false;

        if (ing.safetyScore() != null) return true;
        if (ing.ratingLetter() != null && !ing.ratingLetter().isBlank()) return true;

        Integer refs = ing.referencesCount();
        if (refs != null && refs > 0) return true;

        if (ing.summary() != null && !ing.summary().isBlank()) return true;
        if (ing.description() != null && !ing.description().isBlank()) return true;
        if (ing.func() != null && !ing.func().isBlank()) return true;
        if (ing.concerns() != null && !ing.concerns().isBlank()) return true;
        if (ing.category() != null && !ing.category().isBlank()) return true;
        if (ing.regulationNotes() != null && !ing.regulationNotes().isBlank()) return true;

        List<String> tags = ing.tags();
        if (tags != null && !tags.isEmpty()) return true;

        List<String> aliases = ing.aliases();
        if (aliases != null && !aliases.isEmpty()) return true;

        return false;
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

    private record ProductScoreSnapshot(BigDecimal safetyScore, String ratingLetter) {}
}
