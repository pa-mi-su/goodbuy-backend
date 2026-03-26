package app.goodbuy.products;

import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import app.goodbuy.core.products.port.ProductLookupPort;
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
    private final ProductLookupPort productLookupPort;

    public ProductEvidenceReportController(
            ProductEvidenceReportService service,
            ProductIngredientEvidenceIngestionService ingredientEvidenceIngestionService,
            ProductEvidenceAnalysisService productEvidenceAnalysisService,
            ProductLookupPort productLookupPort
    ) {
        this.service = service;
        this.ingredientEvidenceIngestionService = ingredientEvidenceIngestionService;
        this.productEvidenceAnalysisService = productEvidenceAnalysisService;
        this.productLookupPort = productLookupPort;
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
                result.reprocessQueued(),
                result.status(),
                result.analysisStatus(),
                ingredientRecoveryNextAction(result.analysisStatus(), ingredientReadAvailableNow(productEan)),
                ingredientReadAvailableNow(productEan),
                ingredientRecoveryAvailabilityMessage(
                        result.analysisStatus(),
                        ingredientReadAvailableNow(productEan),
                        result.parsedIngredientCount()
                )
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
                nextAction(entity, evidenceReadyNow(entity, key)),
                rescanAvailableNow(entity, evidenceReadyNow(entity, key))
        );

        // ✅ Always return this shape (client decodes defensively)
        return ResponseEntity.ok(new ProductEvidenceStatusResponse(
                true,
                entity.getStatus(),
                true,
                entity.getAnalysisStatus(),
                nextAction(entity, evidenceReadyNow(entity, key)),
                rescanAvailableNow(entity, evidenceReadyNow(entity, key)),
                availabilityMessage(entity, evidenceReadyNow(entity, key)),
                entity.getParsedIngredientCount() == null ? 0 : entity.getParsedIngredientCount()
        ));
    }

    private boolean productAvailableNow(String key) {
        try {
            return productLookupPort.findByGtin(key).isPresent();
        } catch (Exception ex) {
            log.warn("Product evidence status lookup readiness check failed key={} err={}", key, ex.toString());
            return false;
        }
    }

    private boolean ingredientReadAvailableNow(String key) {
        try {
            return productLookupPort.findByGtin(key)
                    .map(dto -> dto.ingredients() != null && !dto.ingredients().isEmpty())
                    .orElse(false);
        } catch (Exception ex) {
            log.warn("Product evidence ingredient readiness check failed key={} err={}", key, ex.toString());
            return false;
        }
    }

    private boolean evidenceReadyNow(app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity entity, String key) {
        if (ProductEvidenceReportService.REASON_UNCLEAR_INGREDIENTS.equalsIgnoreCase(entity.getReason())) {
            return ingredientReadAvailableNow(key);
        }
        return productAvailableNow(key);
    }

    private static String nextAction(app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity entity, boolean productAvailableNow) {
        String analysisStatus = entity.getAnalysisStatus();
        if ("READY_TO_RESCAN".equalsIgnoreCase(analysisStatus) || ("DRAFT_CREATED".equalsIgnoreCase(analysisStatus) && productAvailableNow)) {
            return "RESCAN_NOW";
        }
        if ("DRAFT_CREATED".equalsIgnoreCase(analysisStatus)) {
            return "CHECK_BACK_LATER";
        }
        if ("REVIEW_REQUIRED".equalsIgnoreCase(analysisStatus)) {
            return "WAIT_FOR_REVIEW";
        }
        if ("ANALYZING".equalsIgnoreCase(analysisStatus)) {
            return "CHECK_BACK_LATER";
        }
        return "WAIT_FOR_UPDATE";
    }

    private static boolean rescanAvailableNow(app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity entity, boolean productAvailableNow) {
        return "READY_TO_RESCAN".equalsIgnoreCase(entity.getAnalysisStatus())
                || ("DRAFT_CREATED".equalsIgnoreCase(entity.getAnalysisStatus()) && productAvailableNow);
    }

    private static String availabilityMessage(app.goodbuy.adapters.core.products.model.ProductEvidenceReportEntity entity, boolean productAvailableNow) {
        String analysisStatus = entity.getAnalysisStatus();
        int parsedIngredientCount = entity.getParsedIngredientCount() == null ? 0 : entity.getParsedIngredientCount();

        if ("READY_TO_RESCAN".equalsIgnoreCase(analysisStatus) || ("DRAFT_CREATED".equalsIgnoreCase(analysisStatus) && productAvailableNow)) {
            return "The draft read is ready. Scan this product again now.";
        }
        if ("DRAFT_CREATED".equalsIgnoreCase(analysisStatus)) {
            return "We are still building the draft read from these photos. Check back in a moment while we finish the product and ingredient decode.";
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

    private static String ingredientRecoveryNextAction(String analysisStatus, boolean ingredientReadAvailableNow) {
        if ("READY_TO_RESCAN".equalsIgnoreCase(analysisStatus) || ("DRAFT_CREATED".equalsIgnoreCase(analysisStatus) && ingredientReadAvailableNow)) {
            return "RESCAN_NOW";
        }
        if ("DRAFT_CREATED".equalsIgnoreCase(analysisStatus) || "ANALYZING".equalsIgnoreCase(analysisStatus)) {
            return "CHECK_BACK_LATER";
        }
        if ("REVIEW_REQUIRED".equalsIgnoreCase(analysisStatus)) {
            return "WAIT_FOR_REVIEW";
        }
        return "WAIT_FOR_UPDATE";
    }

    private static String ingredientRecoveryAvailabilityMessage(String analysisStatus, boolean ingredientReadAvailableNow, int parsedIngredientCount) {
        if ("READY_TO_RESCAN".equalsIgnoreCase(analysisStatus) || ("DRAFT_CREATED".equalsIgnoreCase(analysisStatus) && ingredientReadAvailableNow)) {
            return "The stronger ingredient read is ready. Refresh this product now.";
        }
        if ("DRAFT_CREATED".equalsIgnoreCase(analysisStatus)) {
            return "We extracted the label and we’re rebuilding the ingredient list now.";
        }
        if ("REVIEW_REQUIRED".equalsIgnoreCase(analysisStatus)) {
            if (parsedIngredientCount > 0) {
                return "We extracted some ingredient detail, but it still needs review before the ingredient list can improve.";
            }
            return "We couldn’t read enough from that ingredients photo yet. Try again with a tighter, sharper label shot.";
        }
        if ("ANALYZING".equalsIgnoreCase(analysisStatus)) {
            return "We’ve got the label photo and we’re working through the ingredient recovery now.";
        }
        return "We saved the ingredients photo and will keep processing it in the background.";
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
            String availabilityMessage,
            int parsedIngredientCount
    ) {}

    public record ProductIngredientEvidenceResponse(
            Long id,
            boolean alreadyReported,
            String ocrStatus,
            int parsedIngredientCount,
            boolean reprocessQueued,
            String status,
            String analysisStatus,
            String nextAction,
            boolean rescanAvailableNow,
            String availabilityMessage
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
