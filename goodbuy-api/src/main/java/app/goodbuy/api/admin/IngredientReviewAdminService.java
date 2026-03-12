package app.goodbuy.api.admin;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientAlias;
import app.goodbuy.adapters.core.ingredients.model.IngredientMissingReportEntity;
import app.goodbuy.adapters.core.ingredients.repository.IngredientMissingReportRepository;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.api.admin.IngredientReviewAdminService.ResolveMissingIngredientResult;
import app.goodbuy.products.ProductRawIngredientReprocessingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
public class IngredientReviewAdminService {

    private final IngredientMissingReportRepository missingReportRepository;
    private final IngredientRepository ingredientRepository;
    private final ProductRawIngredientReprocessingService productRawIngredientReprocessingService;

    public IngredientReviewAdminService(
            IngredientMissingReportRepository missingReportRepository,
            IngredientRepository ingredientRepository,
            ProductRawIngredientReprocessingService productRawIngredientReprocessingService
    ) {
        this.missingReportRepository = missingReportRepository;
        this.ingredientRepository = ingredientRepository;
        this.productRawIngredientReprocessingService = productRawIngredientReprocessingService;
    }

    @Transactional(readOnly = true)
    public List<MissingIngredientQueueItem> listOpenQueue(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 250));
        return missingReportRepository.findByStatusOrderByOccurredAtDesc("OPEN").stream()
                .limit(safeLimit)
                .map(entity -> new MissingIngredientQueueItem(
                        entity.getId(),
                        entity.getIngredientName(),
                        entity.getProductEan(),
                        entity.getOccurredAt(),
                        entity.getNotes()
                ))
                .toList();
    }

    @Transactional
    public ResolveMissingIngredientResult resolveMissingIngredient(
            String missingName,
            String canonicalKey,
            String displayName
    ) {
        String normalizedMissing = normalizeDisplay(missingName);
        if (normalizedMissing == null) {
            throw new IllegalArgumentException("missingName is required");
        }

        String resolvedCanonicalKey = normalizeCanonicalKey(firstNonBlank(canonicalKey, displayName, missingName));
        String resolvedDisplayName = firstNonBlank(displayName, missingName);
        if (resolvedCanonicalKey == null || resolvedDisplayName == null) {
            throw new IllegalArgumentException("canonicalKey or displayName is required");
        }

        Ingredient ingredient = ingredientRepository.findByCanonicalKeyIgnoreCase(resolvedCanonicalKey)
                .or(() -> ingredientRepository.findByNameOrAlias(resolvedCanonicalKey.toLowerCase(Locale.ROOT)))
                .or(() -> ingredientRepository.findByNameOrAlias(resolvedDisplayName.toLowerCase(Locale.ROOT)))
                .orElseGet(() -> createIngredient(resolvedCanonicalKey, resolvedDisplayName));

        ensureAlias(ingredient, normalizedMissing);
        Ingredient saved = ingredientRepository.saveAndFlush(ingredient);

        List<IngredientMissingReportEntity> openReports =
                missingReportRepository.findByIngredientNameIgnoreCaseAndStatus(normalizedMissing, "OPEN");

        int reprocessedCount = 0;
        for (IngredientMissingReportEntity report : openReports) {
            report.setStatus("RESOLVED");
            report.setResolvedCanonicalKey(saved.getCanonicalKey());
            report.setResolvedAt(Instant.now());
            if (report.getProductEan() != null
                    && productRawIngredientReprocessingService.reprocessFromStoredRawText(report.getProductEan())) {
                reprocessedCount++;
            }
        }

        missingReportRepository.saveAll(openReports);

        return new ResolveMissingIngredientResult(
                saved.getCanonicalKey(),
                openReports.size(),
                reprocessedCount
        );
    }

    private Ingredient createIngredient(String canonicalKey, String displayName) {
        Ingredient ingredient = new Ingredient();
        ingredient.setCanonicalKey(canonicalKey);
        ingredient.setDisplayName(displayName.trim());
        ingredient.setCategory("unclassified");
        ingredient.setCreatedAt(OffsetDateTime.now());
        ingredient.setUpdatedAt(OffsetDateTime.now());
        ingredient.setReferencesCount(0);
        return ingredient;
    }

    private void ensureAlias(Ingredient ingredient, String aliasValue) {
        boolean alreadyExists = ingredient.getAliases().stream()
                .map(IngredientAlias::getAlias)
                .filter(v -> v != null)
                .map(v -> v.trim().toLowerCase(Locale.ROOT))
                .anyMatch(v -> v.equals(aliasValue.toLowerCase(Locale.ROOT)));

        if (alreadyExists) {
            return;
        }

        IngredientAlias alias = new IngredientAlias();
        alias.setIngredient(ingredient);
        alias.setAlias(aliasValue);
        ingredient.getAliases().add(alias);
    }

    private static String normalizeDisplay(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static String normalizeCanonicalKey(String value) {
        String display = normalizeDisplay(value);
        if (display == null) {
            return null;
        }
        return display.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", " ")
                .trim()
                .replaceAll("\\s+", " ");
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        return java.util.Arrays.stream(values)
                .filter(v -> v != null && !v.isBlank())
                .map(String::trim)
                .findFirst()
                .orElse(null);
    }

    public record MissingIngredientQueueItem(
            Long id,
            String ingredientName,
            String productEan,
            Instant occurredAt,
            String notes
    ) {}

    public record ResolveMissingIngredientResult(
            String canonicalKey,
            int resolvedReports,
            int reprocessedProducts
    ) {}
}
