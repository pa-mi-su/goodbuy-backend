package app.goodbuy.ingredients;

import app.goodbuy.adapters.core.citations.service.IngredientCitationWriter;
import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientAlias;
import app.goodbuy.adapters.core.ingredients.repository.IngredientRepository;
import app.goodbuy.adapters.core.ingredients.service.IngredientSignalsWriter;
import app.goodbuy.core.ingredients.port.IngredientAutoEnricherPort;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentRequest;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class AsyncIngredientResearchWorker {

    private static final Logger log = LoggerFactory.getLogger(AsyncIngredientResearchWorker.class);

    private final IngredientRepository ingredientRepo;
    private final IngredientAutoEnricherPort autoEnricher;
    private final IngredientCitationWriter citationWriter;
    private final IngredientSignalsWriter signalsWriter;

    public AsyncIngredientResearchWorker(
            IngredientRepository ingredientRepo,
            Optional<IngredientAutoEnricherPort> autoEnricher,
            IngredientCitationWriter citationWriter,
            IngredientSignalsWriter signalsWriter
    ) {
        this.ingredientRepo = ingredientRepo;
        this.autoEnricher = autoEnricher.orElse(null);
        this.citationWriter = citationWriter;
        this.signalsWriter = signalsWriter;
    }

    @Async
    @Transactional
    public void researchAsync(long ingredientId, String reason, Runnable onComplete) {
        try {
            if (autoEnricher == null) {
                log.info("AsyncIngredientResearchWorker: auto enricher unavailable ingredientId={} reason={}",
                        ingredientId, safe(reason));
                return;
            }

            Ingredient ingredient = ingredientRepo.findByIdForUpdate(ingredientId).orElse(null);
            if (ingredient == null) {
                log.info("AsyncIngredientResearchWorker: ingredient missing ingredientId={} reason={}",
                        ingredientId, safe(reason));
                return;
            }

            if (!shouldResearch(ingredient)) {
                log.info("AsyncIngredientResearchWorker: ingredient already ready ingredientId={} canonicalKey='{}'",
                        ingredientId, safe(ingredient.getCanonicalKey()));
                return;
            }

            IngredientEnrichmentResult best = findBestResult(ingredient);
            if (best == null || !best.enriched()) {
                log.info("AsyncIngredientResearchWorker: no useful enrichment ingredientId={} canonicalKey='{}'",
                        ingredientId, safe(ingredient.getCanonicalKey()));
                return;
            }

            applyEnrichment(ingredient, best);
            ingredientRepo.saveAndFlush(ingredient);
            attachCitations(ingredient, best);
            signalsWriter.upsertSignalsAndScore(ingredient.getId(), best);
            ingredientRepo.saveAndFlush(ingredient);

            log.info("AsyncIngredientResearchWorker: enriched ingredientId={} canonicalKey='{}' score={} letter={} provider={}",
                    ingredientId,
                    safe(ingredient.getCanonicalKey()),
                    ingredient.getSafetyScore(),
                    safe(ingredient.getRatingLetter()),
                    safe(best.provider()));
        } catch (Exception ex) {
            log.warn("AsyncIngredientResearchWorker: research failed ingredientId={} reason={} type={} msg={}",
                    ingredientId, safe(reason), ex.getClass().getSimpleName(), ex.getMessage(), ex);
        } finally {
            if (onComplete != null) {
                onComplete.run();
            }
        }
    }

    private IngredientEnrichmentResult findBestResult(Ingredient ingredient) {
        IngredientEnrichmentResult best = null;
        int bestScore = Integer.MIN_VALUE;

        for (String query : buildQueries(ingredient)) {
            try {
                IngredientEnrichmentResult result = autoEnricher.enrich(new IngredientEnrichmentRequest(
                        ingredient.getCanonicalKey(),
                        query,
                        Map.of(),
                        "ON_DEMAND_INGREDIENT_LOOKUP",
                        null
                ));

                int score = scoreResult(result);
                if (score > bestScore) {
                    best = result;
                    bestScore = score;
                }

                if (score >= 120) {
                    break;
                }
            } catch (Exception ex) {
                log.warn("AsyncIngredientResearchWorker: query failed ingredientId={} canonicalKey='{}' query='{}' err={}",
                        ingredient.getId(), safe(ingredient.getCanonicalKey()), safe(query), ex.toString());
            }
        }

        return best;
    }

    static List<String> buildQueries(Ingredient ingredient) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        addVariants(queries, ingredient.getDisplayName());
        addVariants(queries, ingredient.getCanonicalKey());
        if (ingredient.getAliases() != null) {
            ingredient.getAliases().stream()
                    .map(IngredientAlias::getAlias)
                    .forEach(alias -> addVariants(queries, alias));
        }
        return new ArrayList<>(queries);
    }

    private static void addVariants(Set<String> queries, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }

        String trimmed = raw.trim();
        maybeAdd(queries, trimmed);
        maybeAdd(queries, trimmed.replaceAll("(?i)^ingredients?:\\s*", ""));
        maybeAdd(queries, trimmed.replaceAll("(?i)^less than\\s+\\d+%\\s+of:?\\s*", ""));

        String withoutParen = trimmed.replaceAll("\\s*\\([^)]*\\)", " ").replaceAll("\\s+", " ").trim();
        maybeAdd(queries, withoutParen);

        String beforeComma = trimmed.split("[,;]")[0].trim();
        maybeAdd(queries, beforeComma);

        String beforeParen = trimmed.replaceAll("\\s*\\(.*$", "").trim();
        maybeAdd(queries, beforeParen);
    }

    private static void maybeAdd(Set<String> queries, String value) {
        if (value != null && !value.isBlank()) {
            queries.add(value.trim());
        }
    }

    private int scoreResult(IngredientEnrichmentResult result) {
        if (result == null || !result.enriched()) {
            return Integer.MIN_VALUE;
        }

        int score = 0;
        if (notBlank(result.summary())) score += 15;
        if (notBlank(result.description())) score += 15;
        if (notBlank(result.functionUse())) score += 12;
        if (notBlank(result.concerns())) score += 12;
        if (notBlank(result.category())) score += 8;
        if (notBlank(result.regulationNotes())) score += 8;
        if (result.referencesCount() != null) score += Math.min(result.referencesCount(), 10) * 3;
        if (result.sourceUrls() != null) score += Math.min(result.sourceUrls().size(), 10) * 3;
        if (result.aliases() != null) score += Math.min(result.aliases().size(), 5) * 2;
        if (result.tags() != null) score += Math.min(result.tags().size(), 5);
        if (result.iarcGroup() != null) score += 10;
        if (Boolean.TRUE.equals(result.prop65Listed())) score += 10;
        if (result.ewgScore() != null) score += 8;
        if (Boolean.TRUE.equals(result.euProhibited()) || Boolean.TRUE.equals(result.euRestricted())) score += 8;
        if (Boolean.TRUE.equals(result.pubchemMutagen()) || Boolean.TRUE.equals(result.pubchemReproductiveToxin())) score += 8;
        if (Boolean.TRUE.equals(result.epaChronicToxicity())) score += 6;
        if (Boolean.TRUE.equals(result.skinIrritant())) score += 4;
        return score;
    }

    private boolean shouldResearch(Ingredient ingredient) {
        return ingredient.getSafetyScore() == null
                || isBlank(ingredient.getSummary())
                || isBlank(ingredient.getDescription())
                || isBlank(ingredient.getFuncUse())
                || isBlank(ingredient.getConcerns())
                || ingredient.getReferencesCount() == null
                || ingredient.getReferencesCount() <= 0;
    }

    private void applyEnrichment(Ingredient ingredient, IngredientEnrichmentResult enrichment) {
        ingredient.setDisplayName(firstNonBlank(enrichment.displayName(), ingredient.getDisplayName(), ingredient.getCanonicalKey()));
        ingredient.setSummary(firstNonBlank(enrichment.summary(), ingredient.getSummary()));
        ingredient.setDescription(firstNonBlank(enrichment.description(), ingredient.getDescription()));
        ingredient.setFuncUse(firstNonBlank(enrichment.functionUse(), ingredient.getFuncUse()));
        ingredient.setConcerns(firstNonBlank(enrichment.concerns(), ingredient.getConcerns()));
        ingredient.setCategory(firstNonBlank(enrichment.category(), ingredient.getCategory()));
        ingredient.setRegulationNotes(firstNonBlank(enrichment.regulationNotes(), ingredient.getRegulationNotes()));
        int currentRefCount = ingredient.getReferencesCount() == null ? 0 : ingredient.getReferencesCount();
        int sourceCount = enrichment.sourceUrls() == null ? 0 : enrichment.sourceUrls().size();
        if (enrichment.referencesCount() != null) {
            ingredient.setReferencesCount(Math.max(currentRefCount, enrichment.referencesCount()));
        } else if (sourceCount > currentRefCount) {
            ingredient.setReferencesCount(sourceCount);
        }
        mergeAliases(ingredient, enrichment.aliases());
        mergeTags(ingredient, enrichment.tags());
    }

    private void attachCitations(Ingredient ingredient, IngredientEnrichmentResult enrichment) {
        if (ingredient.getId() == null || enrichment.sourceUrls() == null || enrichment.sourceUrls().isEmpty()) {
            return;
        }
        citationWriter.attachCitations(
                ingredient.getId(),
                isBlank(enrichment.provider()) ? "Unknown" : enrichment.provider().trim(),
                enrichment.sourceUrls(),
                enrichment.citationTitle()
        );
    }

    private void mergeAliases(Ingredient ingredient, List<String> aliases) {
        if (aliases == null || aliases.isEmpty()) {
            return;
        }

        Set<String> existing = new LinkedHashSet<>();
        if (ingredient.getAliases() != null) {
            ingredient.getAliases().stream()
                    .map(IngredientAlias::getAlias)
                    .filter(Objects::nonNull)
                    .map(AsyncIngredientResearchWorker::normalizeCanonicalKey)
                    .forEach(existing::add);
        }
        existing.add(normalizeCanonicalKey(ingredient.getCanonicalKey()));

        for (String alias : aliases) {
            String normalized = normalizeCanonicalKey(alias);
            if (normalized.isBlank() || !existing.add(normalized)) {
                continue;
            }
            IngredientAlias row = new IngredientAlias();
            row.setIngredient(ingredient);
            row.setAlias(alias.trim());
            ingredient.getAliases().add(row);
        }
    }

    private void mergeTags(Ingredient ingredient, List<String> tags) {
        if (tags == null || tags.isEmpty()) {
            return;
        }

        LinkedHashSet<String> merged = new LinkedHashSet<>();
        if (ingredient.getTags() != null) {
            ingredient.getTags().stream()
                    .filter(Objects::nonNull)
                    .map(String::trim)
                    .filter(s -> !s.isBlank())
                    .forEach(merged::add);
        }
        tags.stream()
                .filter(Objects::nonNull)
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(merged::add);
        ingredient.setTags(new ArrayList<>(merged));
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return null;
    }

    private static boolean notBlank(String value) {
        return !isBlank(value);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String normalizeCanonicalKey(String raw) {
        if (raw == null) {
            return "";
        }
        return raw.toLowerCase()
                .trim()
                .replace('’', '\'')
                .replace('–', '-')
                .replace('—', '-')
                .replaceAll("\\s+", " ");
    }

    private static String safe(String value) {
        return (value == null || value.isBlank()) ? "-" : value.trim();
    }
}
