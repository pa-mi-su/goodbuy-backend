package app.goodbuy.products;

import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products/evidence")
public class ProductEvidenceReportController {

    private static final Logger log = LoggerFactory.getLogger(ProductEvidenceReportController.class);

    private final ProductEvidenceReportService service;
    private final ProductIngredientEvidenceIngestionService ingredientEvidenceIngestionService;
    private final ProductEvidenceAnalysisService productEvidenceAnalysisService;

    public ProductEvidenceReportController(
            ProductEvidenceReportService service,
            ProductIngredientEvidenceIngestionService ingredientEvidenceIngestionService,
            ProductEvidenceAnalysisService productEvidenceAnalysisService
    ) {
        this.service = service;
        this.ingredientEvidenceIngestionService = ingredientEvidenceIngestionService;
        this.productEvidenceAnalysisService = productEvidenceAnalysisService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProductEvidenceReportResponse reportEvidence(
            @RequestPart("productEan") String productEan,
            @RequestPart("reason") String reason,
            @RequestPart(value = "productName", required = false) String productName,
            @RequestPart(value = "brandName", required = false) String brandName,
            @RequestPart(value = "appVersion", required = false) String appVersion,
            @RequestPart(value = "platform", required = false) String platform,
            @RequestPart(value = "notes", required = false) String notes,
            @RequestPart(value = "frontImage", required = false) MultipartFile frontImage,
            @RequestPart(value = "backImage", required = false) MultipartFile backImage
    ) throws Exception {

        var result = service.reportWithStatus(
                productEan,
                reason,
                productName,
                brandName,
                appVersion,
                platform,
                notes,
                frontImage == null ? null : frontImage.getBytes(),
                frontImage == null ? null : frontImage.getContentType(),
                backImage == null ? null : backImage.getBytes(),
                backImage == null ? null : backImage.getContentType()
        );

        return new ProductEvidenceReportResponse(
                result.entity().getId(),
                !result.isNew()
        );
    }

    @PostMapping(
            path = "/ingredients",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProductIngredientEvidenceResponse ingestIngredientEvidence(
            @RequestPart("productEan") String productEan,
            @RequestPart(value = "productName", required = false) String productName,
            @RequestPart(value = "brandName", required = false) String brandName,
            @RequestPart(value = "appVersion", required = false) String appVersion,
            @RequestPart(value = "platform", required = false) String platform,
            @RequestPart(value = "notes", required = false) String notes,
            @RequestPart(value = "ingredientText", required = false) String ingredientText,
            @RequestPart(value = "frontImage", required = false) MultipartFile frontImage,
            @RequestPart(value = "backImage", required = false) MultipartFile backImage
    ) throws Exception {
        var result = ingredientEvidenceIngestionService.ingest(
                productEan,
                productName,
                brandName,
                appVersion,
                platform,
                notes,
                ingredientText,
                frontImage == null ? null : frontImage.getBytes(),
                frontImage == null ? null : frontImage.getContentType(),
                backImage == null ? null : backImage.getBytes(),
                backImage == null ? null : backImage.getContentType()
        );

        return new ProductIngredientEvidenceResponse(
                result.reportId(),
                !result.isNewReport(),
                result.ocrStatus(),
                result.parsedIngredientCount(),
                result.reprocessQueued()
        );
    }

    @PostMapping(
            path = "/analyze",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProductEvidenceAnalysisResponse analyzeProductEvidence(
            @RequestPart("productEan") String productEan,
            @RequestPart(value = "reason", required = false) String reason,
            @RequestPart(value = "productName", required = false) String productName,
            @RequestPart(value = "brandName", required = false) String brandName,
            @RequestPart(value = "appVersion", required = false) String appVersion,
            @RequestPart(value = "platform", required = false) String platform,
            @RequestPart(value = "notes", required = false) String notes,
            @RequestPart(value = "ingredientText", required = false) String ingredientText,
            @RequestPart(value = "frontImage", required = false) MultipartFile frontImage,
            @RequestPart(value = "backImage", required = false) MultipartFile backImage
    ) throws Exception {
        log.info(
                "Product evidence analyze request ean={} reason={} productName={} brandName={} frontImage={} backImage={} ingredientTextPresent={}",
                productEan,
                reason,
                productName,
                brandName,
                frontImage != null && !frontImage.isEmpty(),
                backImage != null && !backImage.isEmpty(),
                ingredientText != null && !ingredientText.isBlank()
        );

        var result = productEvidenceAnalysisService.analyze(
                productEan,
                reason,
                productName,
                brandName,
                appVersion,
                platform,
                notes,
                ingredientText,
                frontImage == null ? null : frontImage.getBytes(),
                frontImage == null ? null : frontImage.getContentType(),
                backImage == null ? null : backImage.getBytes(),
                backImage == null ? null : backImage.getContentType()
        );

        log.info(
                "Product evidence analyze result ean={} reportId={} status={} analysisStatus={} domain={} category={} confidence={} parsedIngredientCount={} draftQueued={} nextAction={} rescanAvailableNow={} alreadyReported={}",
                productEan,
                result.reportId(),
                result.status(),
                result.analysisStatus(),
                result.domain(),
                result.category(),
                result.confidenceScore(),
                result.parsedIngredientCount(),
                result.draftQueued(),
                result.nextAction(),
                result.rescanAvailableNow(),
                result.alreadyReported()
        );

        return new ProductEvidenceAnalysisResponse(
                result.reportId(),
                result.alreadyReported(),
                result.status(),
                result.analysisStatus(),
                result.domain(),
                result.category(),
                result.confidenceScore(),
                result.parsedIngredientCount(),
                result.draftQueued(),
                result.nextAction(),
                result.rescanAvailableNow(),
                result.availabilityMessage()
        );
    }

    /**
     * DB-backed evidence status lookup used by iOS ResultView.
     *
     * Expected client call:
     *   GET /api/v1/products/evidence/status?ean=...&reason=...
     *
     * We accept BOTH query params for compatibility:
     *  - ean
     *  - productEan
     */
    @GetMapping("/status")
    public ResponseEntity<ProductEvidenceStatusResponse> status(
            @RequestParam(value = "ean", required = false) String ean,
            @RequestParam(value = "productEan", required = false) String productEan,
            @RequestParam("reason") String reason
    ) {
        String key = (productEan != null && !productEan.isBlank()) ? productEan : ean;
        log.info("Product evidence status request ean={} productEan={} resolvedKey={} reason={}", ean, productEan, key, reason);
        if (key == null || key.isBlank()) {
            log.warn("Product evidence status bad request reason={} because key was blank", reason);
            return ResponseEntity.badRequest().build();
        }

        var opt = service.findActiveStatus(key, reason);
        if (opt.isEmpty()
                && !ProductEvidenceReportService.REASON_ANALYSIS_REQUESTED.equalsIgnoreCase(reason)) {
            opt = service.findActiveStatus(key, ProductEvidenceReportService.REASON_ANALYSIS_REQUESTED);
        }

        if (opt.isEmpty()) {
            // ✅ IMPORTANT: 204 means “not reported” (not an error)
            log.info("Product evidence status not found resolvedKey={} reason={} returning=204", key, reason);
            return ResponseEntity.noContent().build();
        }

        var entity = opt.get();
        log.info(
                "Product evidence status found resolvedKey={} reason={} entityStatus={} analysisStatus={} reportId={} nextAction={} rescanAvailableNow={}",
                key,
                reason,
                entity.getStatus(),
                entity.getAnalysisStatus(),
                entity.getId(),
                nextAction(entity),
                rescanAvailableNow(entity)
        );

        // ✅ Always return this shape (client decodes defensively)
        return ResponseEntity.ok(new ProductEvidenceStatusResponse(
                true,
                entity.getStatus(),
                true,
                entity.getAnalysisStatus(),
                nextAction(entity),
                rescanAvailableNow(entity),
                availabilityMessage(entity)
        ));
    }

    private static String nextAction(app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity entity) {
        String analysisStatus = entity.getAnalysisStatus();
        if ("READY_TO_RESCAN".equalsIgnoreCase(analysisStatus)) {
            return "RESCAN_NOW";
        }
        if ("DRAFT_CREATED".equalsIgnoreCase(analysisStatus)) {
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

    private static boolean rescanAvailableNow(app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity entity) {
        return "READY_TO_RESCAN".equalsIgnoreCase(entity.getAnalysisStatus());
    }

    private static String availabilityMessage(app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity entity) {
        String analysisStatus = entity.getAnalysisStatus();
        int parsedIngredientCount = entity.getParsedIngredientCount() == null ? 0 : entity.getParsedIngredientCount();

        if ("READY_TO_RESCAN".equalsIgnoreCase(analysisStatus)) {
            return "The draft read is ready. Scan this product again now.";
        }
        if ("DRAFT_CREATED".equalsIgnoreCase(analysisStatus)) {
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

    public record ProductEvidenceReportResponse(
            Long id,
            boolean alreadyReported
    ) {}

    public record ProductEvidenceStatusResponse(
            boolean exists,
            String status,
            boolean alreadyReported,
            String analysisStatus,
            String nextAction,
            boolean rescanAvailableNow,
            String availabilityMessage
    ) {}

    public record ProductIngredientEvidenceResponse(
            Long id,
            boolean alreadyReported,
            String ocrStatus,
            int parsedIngredientCount,
            boolean reprocessQueued
    ) {}

    public record ProductEvidenceAnalysisResponse(
            Long id,
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
