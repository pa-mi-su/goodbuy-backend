package app.goodbuy.products;

import app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity;
import app.goodbuy.adapters.core.products.repo.ProductEvidenceReportRepository;
import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.ingredients.IngredientTextParser;
import app.goodbuy.core.products.port.ProductEvidenceAnalyzerPort;
import app.goodbuy.core.products.port.ProductIngredientOcrPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class ProductEvidenceAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(ProductEvidenceAnalysisService.class);

    private static final int AUTO_DRAFT_CONFIDENCE = 72;
    private static final List<String> INGREDIENT_SECTION_MARKERS = List.of(
            "ingredients:",
            "ingredient:",
            "other ingredients:",
            "inactive ingredients:",
            "active ingredients:"
    );
    private static final List<String> SECTION_STOP_MARKERS = List.of(
            "directions",
            "warning",
            "warnings",
            "drug facts",
            "questions",
            "distributed by",
            "distribuido por",
            "made in",
            "uses",
            "uses:",
            "purpose",
            "purpose:",
            "keep out of reach",
            "safety tip",
            "tear free",
            "no more tears",
            "no parabens",
            "no phthalates",
            "phthalates or dyes",
            "sulfates or dyes",
            "gentle enough",
            "hypoallergenic",
            "compare to",
            "safety seal"
    );

    private final ProductEvidenceReportService evidenceReportService;
    private final ProductEvidenceReportRepository evidenceReportRepository;
    private final AsyncProductIngestionService asyncProductIngestionService;
    private final ProductIngredientOcrPort ingredientOcrPort;
    private final ProductEvidenceAnalyzerPort analyzerPort;

    public ProductEvidenceAnalysisService(
            ProductEvidenceReportService evidenceReportService,
            ProductEvidenceReportRepository evidenceReportRepository,
            AsyncProductIngestionService asyncProductIngestionService,
            Optional<ProductIngredientOcrPort> ingredientOcrPort,
            Optional<ProductEvidenceAnalyzerPort> analyzerPort
    ) {
        this.evidenceReportService = evidenceReportService;
        this.evidenceReportRepository = evidenceReportRepository;
        this.asyncProductIngestionService = asyncProductIngestionService;
        this.ingredientOcrPort = ingredientOcrPort.orElse(null);
        this.analyzerPort = analyzerPort.orElse(null);
    }

    @Transactional
    public ProductEvidenceAnalysisResult analyze(
            String productEan,
            String originalReason,
            String productName,
            String brandName,
            String appVersion,
            String platform,
            String notes,
            String manualIngredientText,
            byte[] frontImageBytes,
            String frontContentType,
            byte[] backImageBytes,
            String backContentType
    ) {
        log.info(
                "Analyze product evidence start ean={} originalReason={} productName={} brandName={} manualIngredientTextPresent={} frontBytes={} backBytes={} analyzerAvailable={} ocrAvailable={}",
                productEan,
                originalReason,
                productName,
                brandName,
                manualIngredientText != null && !manualIngredientText.isBlank(),
                frontImageBytes == null ? 0 : frontImageBytes.length,
                backImageBytes == null ? 0 : backImageBytes.length,
                analyzerPort != null,
                ingredientOcrPort != null
        );

        var report = evidenceReportService.reportWithStatus(
                productEan,
                ProductEvidenceReportService.REASON_ANALYSIS_REQUESTED,
                productName,
                brandName,
                appVersion,
                platform,
                notes,
                frontImageBytes,
                frontContentType,
                backImageBytes,
                backContentType
        );

        ProductEvidenceReportEntity entity = report.entity();
        entity.setStatus(ProductEvidenceReportService.STATUS_ANALYZING);
        entity.setAnalysisStatus("ANALYZING");

        OcrOutcome ocrOutcome = resolveText(
                entity,
                manualIngredientText,
                frontImageBytes,
                frontContentType,
                backImageBytes,
                backContentType
        );

        entity.setOcrStatus(ocrOutcome.status());
        entity.setOcrProvider(ocrOutcome.provider());
        entity.setOcrRawText(ocrOutcome.rawText());
        entity.setParsedIngredientText(ocrOutcome.parsedText());
        entity.setParsedIngredientCount(ocrOutcome.ingredients().size());

        log.info(
                "Analyze product evidence OCR ean={} status={} provider={} rawTextPresent={} parsedIngredientCount={}",
                productEan,
                ocrOutcome.status(),
                ocrOutcome.provider(),
                ocrOutcome.rawText() != null && !ocrOutcome.rawText().isBlank(),
                ocrOutcome.ingredients().size()
        );

        AnalysisOutcome analysisOutcome = analyzeEvidence(
                entity.getEan(),
                originalReason,
                firstNonBlank(productName, entity.getProductName()),
                firstNonBlank(brandName, entity.getBrand()),
                ocrOutcome,
                frontImageBytes != null && frontImageBytes.length > 0,
                backImageBytes != null && backImageBytes.length > 0
        );

        entity.setAnalysisProvider(analysisOutcome.provider());
        entity.setAnalysisConfidence(analysisOutcome.confidenceScore());
        entity.setAnalysisDomain(analysisOutcome.domain());
        entity.setAnalysisCategory(analysisOutcome.category());
        entity.setAnalysisProductName(analysisOutcome.productName());
        entity.setAnalysisBrand(analysisOutcome.brandName());
        entity.setAnalysisSummary(analysisOutcome.summary());
        entity.setAnalysisRawPayload(analysisOutcome.rawPayload());

        log.info(
                "Analyze product evidence AI result ean={} provider={} domain={} category={} confidence={} ingredientCount={} productName={} brandName={}",
                productEan,
                analysisOutcome.provider(),
                analysisOutcome.domain(),
                analysisOutcome.category(),
                analysisOutcome.confidenceScore(),
                analysisOutcome.ingredients().size(),
                analysisOutcome.productName(),
                analysisOutcome.brandName()
        );

        boolean draftQueued = shouldAutoDraft(analysisOutcome);
        if (draftQueued) {
            entity.setStatus(ProductEvidenceReportService.STATUS_DRAFT_CREATED);
            entity.setAnalysisStatus("DRAFT_CREATED");
            entity.setLastReprocessedAt(OffsetDateTime.now());
            entity.setDraftCreatedAt(OffsetDateTime.now());
            asyncProductIngestionService.enqueue(buildDraftDto(entity, analysisOutcome));
            log.info("Analyze product evidence queued draft ean={} confidence={} ingredientCount={}", productEan, analysisOutcome.confidenceScore(), analysisOutcome.ingredients().size());
        } else {
            entity.setStatus(ProductEvidenceReportService.STATUS_REVIEW_REQUIRED);
            entity.setAnalysisStatus("REVIEW_REQUIRED");
            log.info("Analyze product evidence marked review required ean={} confidence={} ingredientCount={}", productEan, analysisOutcome.confidenceScore(), analysisOutcome.ingredients().size());
        }

        evidenceReportRepository.save(entity);

        String nextAction = nextAction(entity.getAnalysisStatus(), draftQueued);
        boolean rescanAvailableNow = rescanAvailableNow(entity.getAnalysisStatus(), draftQueued);
        String availabilityMessage = availabilityMessage(
                entity.getAnalysisStatus(),
                draftQueued,
                entity.getParsedIngredientCount() == null ? 0 : entity.getParsedIngredientCount()
        );

        log.info(
                "Analyze product evidence complete ean={} reportId={} status={} analysisStatus={} parsedIngredientCount={} draftQueued={} nextAction={} rescanAvailableNow={} availabilityMessage={}",
                productEan,
                entity.getId(),
                entity.getStatus(),
                entity.getAnalysisStatus(),
                entity.getParsedIngredientCount() == null ? 0 : entity.getParsedIngredientCount(),
                draftQueued,
                nextAction,
                rescanAvailableNow,
                availabilityMessage
        );

        return new ProductEvidenceAnalysisResult(
                entity.getId(),
                report.isNew(),
                entity.getStatus(),
                entity.getAnalysisStatus(),
                entity.getAnalysisDomain(),
                entity.getAnalysisCategory(),
                entity.getAnalysisConfidence(),
                entity.getParsedIngredientCount() == null ? 0 : entity.getParsedIngredientCount(),
                draftQueued,
                nextAction,
                rescanAvailableNow,
                availabilityMessage
        );
    }

    private OcrOutcome resolveText(
            ProductEvidenceReportEntity entity,
            String manualIngredientText,
            byte[] frontImageBytes,
            String frontContentType,
            byte[] backImageBytes,
            String backContentType
    ) {
        String rawText = trimToNull(manualIngredientText);
        String provider = null;
        String status = "NOT_REQUESTED";

        if (rawText != null) {
            provider = "manual";
            status = "COMPLETED";
        } else if (ingredientOcrPort != null && (frontImageBytes != null || backImageBytes != null)) {
            Optional<ProductIngredientOcrPort.ProductIngredientOcrResult> opt = ingredientOcrPort.extract(
                    new ProductIngredientOcrPort.ProductIngredientOcrRequest(
                            entity.getEan(),
                            entity.getProductName(),
                            entity.getBrand(),
                            frontImageBytes,
                            frontContentType,
                            backImageBytes,
                            backContentType
                    )
            );
            if (opt.isPresent() && trimToNull(opt.get().rawText()) != null) {
                rawText = trimToNull(opt.get().rawText());
                provider = trimToNull(opt.get().provider());
                status = "COMPLETED";
            } else {
                status = "NO_TEXT";
            }
        } else if (frontImageBytes != null || backImageBytes != null) {
            status = "NOT_AVAILABLE";
        }

        String ingredientPanelText = ingredientPanelText(rawText);
        List<String> ingredients = filterEvidenceIngredients(IngredientTextParser.parse(ingredientPanelText));
        String parsedText = ingredients.isEmpty() ? null : String.join(", ", ingredients);
        if (rawText != null && !rawText.isBlank()) {
            log.info(
                    "Analyze product evidence OCR filter ean={} ingredientPanelDetected={} parsedIngredientCount={} rawChars={}",
                    entity.getEan(),
                    ingredientPanelText != null && !ingredientPanelText.isBlank(),
                    ingredients.size(),
                    rawText.length()
            );
        }
        return new OcrOutcome(status, provider, rawText, parsedText, ingredients);
    }

    private AnalysisOutcome analyzeEvidence(
            String productEan,
            String originalReason,
            String productName,
            String brandName,
            OcrOutcome ocrOutcome,
            boolean frontImageProvided,
            boolean backImageProvided
    ) {
        ProductEvidenceAnalyzerPort.ProductEvidenceAnalysisResult ai = analyzerPort == null
                ? null
                : analyzerPort.analyze(new ProductEvidenceAnalyzerPort.ProductEvidenceAnalysisRequest(
                        productEan,
                        originalReason,
                        productName,
                        brandName,
                        ocrOutcome.rawText(),
                        ocrOutcome.parsedText(),
                        frontImageProvided,
                        backImageProvided
                )).orElse(null);

        List<String> ingredients = filterEvidenceIngredients(mergeIngredients(
                ai == null ? List.of() : ai.likelyIngredients(),
                ocrOutcome.ingredients()
        ));

        String resolvedDomain = normalizeDomain(ai == null ? null : ai.domain(), productName, brandName, ocrOutcome.rawText());
        String resolvedCategory = firstNonBlank(
                ai == null ? null : ai.category(),
                inferCategory(resolvedDomain, productName, ocrOutcome.rawText())
        );

        int confidence = boundedConfidence(
                ai == null ? heuristicConfidence(productName, brandName, resolvedDomain, ingredients, ocrOutcome.rawText())
                        : Math.max(
                        ai.confidenceScore() == null ? 0 : ai.confidenceScore(),
                        heuristicConfidence(productName, brandName, resolvedDomain, ingredients, ocrOutcome.rawText())
                )
        );

        return new AnalysisOutcome(
                firstNonBlank(ai == null ? null : ai.provider(), "HEURISTIC"),
                firstNonBlank(ai == null ? null : ai.productName(), productName, fallbackProductName(productEan, resolvedDomain)),
                firstNonBlank(ai == null ? null : ai.brandName(), brandName),
                resolvedDomain,
                resolvedCategory,
                ingredients,
                confidence,
                confidenceBand(confidence),
                firstNonBlank(ai == null ? null : ai.summary(), buildSummary(resolvedDomain, ingredients.size(), confidence)),
                ai == null ? null : ai.rawPayload()
        );
    }

    private ProductDetailDto buildDraftDto(ProductEvidenceReportEntity entity, AnalysisOutcome outcome) {
        List<ProductDetailDto.IngredientDto> ingredients = outcome.ingredients().stream()
                .map(label -> new ProductDetailDto.IngredientDto(
                        null,
                        label,
                        label,
                        Map.of(),
                        null,
                        null
                ))
                .toList();

        List<ProductDetailDto.ImageDto> images = Optional.ofNullable(trimToNull(entity.getFrontImageS3Url()))
                .map(url -> List.of(new ProductDetailDto.ImageDto(url, null, null)))
                .orElseGet(List::of);

        return new ProductDetailDto(
                entity.getEan(),
                outcome.productName(),
                outcome.brandName(),
                outcome.category(),
                outcome.summary(),
                images,
                ingredients,
                Map.of(),
                Map.of(),
                "AI-PRODUCT-INTAKE",
                outcome.domain(),
                null,
                null
        );
    }

    private static boolean shouldAutoDraft(AnalysisOutcome outcome) {
        return outcome != null
                && outcome.confidenceScore() >= AUTO_DRAFT_CONFIDENCE
                && outcome.productName() != null
                && !outcome.productName().isBlank()
                && outcome.ingredients().size() >= 2;
    }

    private static String nextAction(String analysisStatus, boolean draftQueued) {
        if ("READY_TO_RESCAN".equalsIgnoreCase(analysisStatus)) {
            return "RESCAN_NOW";
        }
        if (draftQueued || "DRAFT_CREATED".equalsIgnoreCase(analysisStatus)) {
            return "RESCAN_SOON";
        }
        if ("REVIEW_REQUIRED".equalsIgnoreCase(analysisStatus)) {
            return "WAIT_FOR_REVIEW";
        }
        if ("ANALYZING".equalsIgnoreCase(analysisStatus)) {
            return "CHECK_BACK_LATER";
        }
        return "WAIT_FOR_UPDATE";
    }

    private static boolean rescanAvailableNow(String analysisStatus, boolean draftQueued) {
        return "READY_TO_RESCAN".equalsIgnoreCase(analysisStatus);
    }

    private static String availabilityMessage(String analysisStatus, boolean draftQueued, int parsedIngredientCount) {
        if ("READY_TO_RESCAN".equalsIgnoreCase(analysisStatus)) {
            return "The draft read is ready. Scan this product again now.";
        }
        if (draftQueued || "DRAFT_CREATED".equalsIgnoreCase(analysisStatus)) {
            return "We started building a draft read from these photos. Try scanning again in a minute or two.";
        }
        if ("REVIEW_REQUIRED".equalsIgnoreCase(analysisStatus)) {
            if (parsedIngredientCount > 0) {
                return "We extracted some label detail, but this product still needs review before a rescan will improve.";
            }
            return "We could not read enough from the label yet. A sharper label photo or manual review is needed before rescanning will help.";
        }
        if ("ANALYZING".equalsIgnoreCase(analysisStatus)) {
            return "We are still analyzing these photos. Give us a little time before trying again.";
        }
        return "We saved this submission and will keep processing it in the background.";
    }

    private static List<String> mergeIngredients(List<String> primary, List<String> fallback) {
        Set<String> merged = new LinkedHashSet<>();
        addAll(merged, primary);
        addAll(merged, fallback);
        return merged.stream().limit(64).toList();
    }

    private static void addAll(Set<String> out, List<String> values) {
        if (values == null) {
            return;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                out.add(trimmed);
            }
        }
    }

    private static List<String> filterEvidenceIngredients(List<String> values) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }

        List<String> filtered = new ArrayList<>();
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed == null || !looksLikeIngredientCandidate(trimmed)) {
                continue;
            }
            filtered.add(trimmed);
        }
        return filtered.stream().distinct().limit(64).toList();
    }

    private static String ingredientPanelText(String rawText) {
        String text = trimToNull(rawText);
        if (text == null) {
            return null;
        }

        String lower = text.toLowerCase(Locale.ROOT);
        int start = -1;
        for (String marker : INGREDIENT_SECTION_MARKERS) {
            int idx = lower.indexOf(marker);
            if (idx >= 0 && (start == -1 || idx < start)) {
                start = idx + marker.length();
            }
        }

        if (start < 0) {
            return null;
        }

        String panel = text.substring(Math.min(start, text.length())).trim();
        String panelLower = panel.toLowerCase(Locale.ROOT);
        int end = panel.length();
        for (String stopMarker : SECTION_STOP_MARKERS) {
            int idx = panelLower.indexOf(stopMarker);
            if (idx > 0 && idx < end) {
                end = idx;
            }
        }

        return trimToNull(panel.substring(0, end));
    }

    private static boolean looksLikeIngredientCandidate(String value) {
        String lower = value.toLowerCase(Locale.ROOT).trim();
        if (lower.isBlank()) {
            return false;
        }
        if (lower.length() > 80) {
            return false;
        }
        if (!lower.matches(".*[a-z].*")) {
            return false;
        }
        if (lower.matches(".*\\b\\d{5,}\\b.*")) {
            return false;
        }
        if (lower.matches(".*\\b\\d+(\\.\\d+)?\\s*(ml|fl\\s?oz|floz|oz|g|mg|mcg|kg|lb|lbs)\\b.*")) {
            return false;
        }
        if (containsAny(lower,
                "made in",
                "distributed by",
                "compare to",
                "safety tip",
                "only 11 ingredients",
                "only ingredients",
                "tear free",
                "no more tears",
                "hypoallergenic",
                "gentle enough",
                "wash & shampoo",
                "bath and shampoo",
                "baño y champú",
                "no parabens",
                "no phthalates",
                "sulfates or dyes",
                "j&jci",
                "questions or comments",
                "squirt a",
                "clean away",
                "increase the amount",
                "greasier items",
                "onto a sponge",
                "how to use",
                "use a little",
                "apply to"
        )) {
            return false;
        }
        if (lower.matches(".*\\b(squirt|clean|increase|apply|use|rub|rinse|wipe)\\b.*")) {
            return false;
        }
        if (lower.startsWith("no ") || lower.startsWith("free of ") || lower.startsWith("free from ")) {
            return false;
        }
        if (lower.contains("®") || lower.contains("™")) {
            return false;
        }
        return true;
    }

    private static int heuristicConfidence(String productName, String brandName, String domain, List<String> ingredients, String rawText) {
        int score = 20;
        if (trimToNull(productName) != null) score += 15;
        if (trimToNull(brandName) != null) score += 10;
        if (trimToNull(domain) != null && !"unknown".equals(domain)) score += 15;
        if (ingredients != null) score += Math.min(35, ingredients.size() * 5);
        if (trimToNull(rawText) != null && rawText.length() > 120) score += 10;
        return score;
    }

    private static int boundedConfidence(int score) {
        return Math.max(0, Math.min(100, score));
    }

    private static String confidenceBand(int score) {
        if (score >= 80) return "high";
        if (score >= 60) return "medium";
        return "low";
    }

    private static String normalizeDomain(String aiDomain, String productName, String brandName, String rawText) {
        String normalized = normalizeValue(aiDomain);
        if (!normalized.isBlank()) {
            return switch (normalized) {
                case "soap", "skincare", "skin-care", "cosmetics", "personal care", "personal-care", "beauty" -> "personal-care";
                case "cleaners", "detergent", "household cleaner" -> "cleaning";
                case "medication", "drug", "otc", "medicine", "supplement-drug" -> "medicine";
                default -> normalized;
            };
        }

        String haystack = String.join(" ",
                normalizeValue(productName),
                normalizeValue(brandName),
                normalizeValue(rawText)
        );

        if (containsAny(haystack, "drug facts", "dosage", "acetaminophen", "ibuprofen", "tablet", "capslet", "capsule")) return "medicine";
        if (containsAny(haystack, "vitamin", "supplement facts", "multivitamin", "biotin", "omega-3")) return "vitamins";
        if (containsAny(haystack, "soap", "shampoo", "conditioner", "lotion", "serum", "body wash", "moisturizer", "deodorant")) return "personal-care";
        if (containsAny(haystack, "cleaner", "detergent", "disinfect", "bleach", "laundry", "dish soap", "all purpose")) return "cleaning";
        if (containsAny(haystack, "baby", "infant", "toddler", "newborn")) return "baby";
        if (containsAny(haystack, "nutrition facts", "serving size", "calories", "snack", "beverage", "drink", "food")) return "food";
        if (containsAny(haystack, "household", "odor eliminator", "air freshener")) return "household";
        return "other";
    }

    private static String inferCategory(String domain, String productName, String rawText) {
        String hint = firstNonBlank(productName, rawText, domain);
        if (hint == null) {
            return "Consumer Product";
        }
        String normalized = hint.toLowerCase(Locale.ROOT);
        if ("medicine".equals(domain)) return containsAny(normalized, "capsule", "tablet") ? "Medication" : "OTC Medicine";
        if ("vitamins".equals(domain)) return "Vitamins & Supplements";
        if ("food".equals(domain)) return "Food & Beverage";
        if ("cleaning".equals(domain)) return "Home Cleaning";
        if ("personal-care".equals(domain)) return "Personal Care";
        if ("baby".equals(domain)) return "Baby Care";
        if ("household".equals(domain)) return "Household Product";
        return "Consumer Product";
    }

    private static String fallbackProductName(String ean, String domain) {
        String label = firstNonBlank(domain, "product").replace('-', ' ');
        return "Scanned " + titleCase(label) + " " + ean.substring(Math.max(0, ean.length() - 4));
    }

    private static String buildSummary(String domain, int ingredientCount, int confidence) {
        return "GoodBuy captured this " + titleCase(domain.replace('-', ' '))
                + " from user-submitted photos and extracted "
                + ingredientCount + " likely ingredient" + (ingredientCount == 1 ? "" : "s")
                + " with " + confidenceBand(confidence) + " confidence.";
    }

    private static boolean containsAny(String haystack, String... needles) {
        if (haystack == null || haystack.isBlank() || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (needle != null && !needle.isBlank() && haystack.contains(needle.toLowerCase(Locale.ROOT))) {
                return true;
            }
        }
        return false;
    }

    private static String titleCase(String value) {
        String normalized = normalizeValue(value).replace('-', ' ');
        if (normalized.isBlank()) {
            return "Product";
        }
        String[] parts = normalized.split("\\s+");
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (part.isBlank()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(part.charAt(0)));
            out.append(part.substring(1));
        }
        return out.toString();
    }

    private static String normalizeValue(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            String trimmed = trimToNull(value);
            if (trimmed != null) {
                return trimmed;
            }
        }
        return null;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private record OcrOutcome(
            String status,
            String provider,
            String rawText,
            String parsedText,
            List<String> ingredients
    ) {}

    private record AnalysisOutcome(
            String provider,
            String productName,
            String brandName,
            String domain,
            String category,
            List<String> ingredients,
            int confidenceScore,
            String confidenceBand,
            String summary,
            String rawPayload
    ) {}

    public record ProductEvidenceAnalysisResult(
            Long reportId,
            boolean alreadyReported,
            String status,
            String analysisStatus,
            String domain,
            String category,
            Integer confidenceScore,
            int parsedIngredientCount,
            boolean draftQueued,
            String nextAction,
            boolean rescanAvailableNow,
            String availabilityMessage
    ) {}
}
