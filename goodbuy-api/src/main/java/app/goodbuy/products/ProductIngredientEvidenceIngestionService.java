package app.goodbuy.products;

import app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity;
import app.goodbuy.adapters.core.products.repo.ProductEvidenceReportRepository;
import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.ingredients.RecoveredIngredientExtractor;
import app.goodbuy.core.products.port.ProductIngredientOcrPort;
import app.goodbuy.core.products.port.ProductLookupPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import app.goodbuy.core.products.StrictProductIngestionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Service
public class ProductIngredientEvidenceIngestionService {

    private static final Logger log = LoggerFactory.getLogger(ProductIngredientEvidenceIngestionService.class);

    private final ProductEvidenceReportService evidenceReportService;
    private final ProductEvidenceReportRepository evidenceReportRepository;
    private final ProductLookupPort productLookupPort;
    private final app.goodbuy.core.products.port.ExternalCatalogClient externalCatalogClient;
    private final AsyncProductIngestionService asyncProductIngestionService;
    private final ProductIngredientOcrPort ingredientOcrPort;
    private final ProductSnapshotPort productSnapshotPort;

    public ProductIngredientEvidenceIngestionService(
            ProductEvidenceReportService evidenceReportService,
            ProductEvidenceReportRepository evidenceReportRepository,
            Optional<ProductLookupPort> productLookupPort,
            Optional<app.goodbuy.core.products.port.ExternalCatalogClient> externalCatalogClient,
            AsyncProductIngestionService asyncProductIngestionService,
            Optional<ProductIngredientOcrPort> ingredientOcrPort,
            Optional<ProductSnapshotPort> productSnapshotPort
    ) {
        this.evidenceReportService = evidenceReportService;
        this.evidenceReportRepository = evidenceReportRepository;
        this.productLookupPort = productLookupPort.orElse(null);
        this.externalCatalogClient = externalCatalogClient.orElse(null);
        this.asyncProductIngestionService = asyncProductIngestionService;
        this.ingredientOcrPort = ingredientOcrPort.orElse(null);
        this.productSnapshotPort = productSnapshotPort.orElse(null);
    }

    @Transactional
    public ProductIngredientEvidenceIngestionResult ingest(
            String productEan,
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
        var reportResult = evidenceReportService.reportWithStatus(
                productEan,
                ProductEvidenceReportService.REASON_UNCLEAR_INGREDIENTS,
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

        ProductEvidenceReportEntity entity = reportResult.entity();
        OcrOutcome outcome = resolveText(
                entity,
                manualIngredientText,
                frontImageBytes,
                frontContentType,
                backImageBytes,
                backContentType
        );

        entity.setOcrStatus(outcome.status());
        entity.setOcrProvider(outcome.provider());
        entity.setOcrRawText(outcome.rawText());
        entity.setParsedIngredientText(outcome.parsedText());
        entity.setParsedIngredientCount(outcome.ingredients().size());

        boolean queued = false;
        if (outcome.allowAutoReprocess() && !outcome.ingredients().isEmpty()) {
            ProductDetailDto reprocessDto = buildReprocessDto(
                    entity.getEan(),
                    firstNonBlank(productName, entity.getProductName()),
                    firstNonBlank(brandName, entity.getBrand()),
                    outcome.ingredients()
            );
            entity.setLastReprocessedAt(OffsetDateTime.now());

            if (productSnapshotPort != null) {
                try {
                    productSnapshotPort.saveSnapshot(reprocessDto);
                    entity.setStatus("READY_TO_RESCAN");
                    entity.setAnalysisStatus("READY_TO_RESCAN");
                    queued = true;
                    log.info("ProductIngredientEvidenceIngestionService: saved recovery snapshot immediately ean={} ingredientCount={}",
                            entity.getEan(), outcome.ingredients().size());
                } catch (StrictProductIngestionException ex) {
                    entity.setStatus(ProductEvidenceReportService.STATUS_REVIEW_REQUIRED);
                    entity.setAnalysisStatus(ProductEvidenceReportService.STATUS_REVIEW_REQUIRED);
                    log.warn("ProductIngredientEvidenceIngestionService: sync recovery snapshot incomplete ean={} msg={}",
                            entity.getEan(), ex.getMessage());
                }
            } else {
                entity.setStatus(ProductEvidenceReportService.STATUS_DRAFT_CREATED);
                entity.setAnalysisStatus(ProductEvidenceReportService.STATUS_DRAFT_CREATED);
                asyncProductIngestionService.enqueue(reprocessDto);
                queued = true;
                log.info("ProductIngredientEvidenceIngestionService: queued reprocess ean={} ingredientCount={}",
                        entity.getEan(), outcome.ingredients().size());
            }
        } else {
            entity.setStatus(ProductEvidenceReportService.STATUS_REVIEW_REQUIRED);
            entity.setAnalysisStatus(ProductEvidenceReportService.STATUS_REVIEW_REQUIRED);
        }

        evidenceReportRepository.save(entity);

        return new ProductIngredientEvidenceIngestionResult(
                entity.getId(),
                reportResult.isNew(),
                entity.getOcrStatus(),
                outcome.ingredients().size(),
                queued,
                entity.getStatus(),
                entity.getAnalysisStatus()
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
        boolean manualSubmission = rawText != null;

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

        List<String> ingredients = RecoveredIngredientExtractor.extract(rawText);
        String parsedText = ingredients.isEmpty() ? null : String.join(", ", ingredients);
        boolean allowAutoReprocess = shouldAutoReprocess(rawText, ingredients, manualSubmission);

        if (rawText != null && ingredients.isEmpty()) {
            status = "PARSE_EMPTY";
        } else if (rawText != null && !allowAutoReprocess) {
            status = "LOW_CONFIDENCE";
        }

        return new OcrOutcome(status, provider, rawText, parsedText, ingredients, allowAutoReprocess);
    }

    private static boolean shouldAutoReprocess(String rawText, List<String> ingredients, boolean manualSubmission) {
        if (rawText == null || rawText.isBlank() || ingredients == null || ingredients.isEmpty()) {
            return false;
        }

        if (manualSubmission) {
            return ingredients.size() >= 2;
        }

        String normalized = rawText.toLowerCase(Locale.ROOT);
        boolean hasIngredientMarker = normalized.contains("ingredient")
                || normalized.contains("other ingredients")
                || normalized.contains("inactive ingredients")
                || normalized.contains("contains less than")
                || normalized.contains("less than 2%");

        boolean enoughParsedIngredients = ingredients.size() >= 5;
        boolean markerBackedShortList = hasIngredientMarker && ingredients.size() >= 3;

        return enoughParsedIngredients || markerBackedShortList;
    }

    private ProductDetailDto buildReprocessDto(
            String ean,
            String fallbackName,
            String fallbackBrand,
            List<String> ingredientLabels
    ) {
        ProductDetailDto base = productLookupPort == null
                ? null
                : productLookupPort.findByGtin(ean).orElse(null);
        if (base == null && externalCatalogClient != null) {
            base = externalCatalogClient.findByGtin(ean).orElse(null);
        }

        List<ProductDetailDto.IngredientDto> ingredients = ingredientLabels.stream()
                .map(label -> new ProductDetailDto.IngredientDto(
                        null,
                        label,
                        label,
                        Map.of(),
                        null,
                        null
                ))
                .toList();

        return new ProductDetailDto(
                firstNonBlank(base == null ? null : base.gtin(), ean),
                firstNonBlank(base == null ? null : base.name(), fallbackName),
                firstNonBlank(base == null ? null : base.brand(), fallbackBrand),
                base == null ? null : base.category(),
                base == null ? null : base.description(),
                base == null ? List.of() : safeList(base.images()),
                ingredients,
                base == null ? Map.of() : safeMap(base.titles()),
                base == null ? Map.of() : safeMap(base.manufacturer()),
                "AI-PRODUCT-INTAKE",
                firstNonBlank(base == null ? null : base.domain(), "unknown"),
                null,
                null
        );
    }

    private static <T> List<T> safeList(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static <K, V> Map<K, V> safeMap(Map<K, V> value) {
        return value == null ? Map.of() : value;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
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
            List<String> ingredients,
            boolean allowAutoReprocess
    ) {}

    public record ProductIngredientEvidenceIngestionResult(
            Long reportId,
            boolean isNewReport,
            String ocrStatus,
            int parsedIngredientCount,
            boolean reprocessQueued,
            String status,
            String analysisStatus
    ) {}
}
