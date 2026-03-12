package app.goodbuy.products;

import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products/evidence")
public class ProductEvidenceReportController {

    private final ProductEvidenceReportService service;
    private final ProductIngredientEvidenceIngestionService ingredientEvidenceIngestionService;

    public ProductEvidenceReportController(
            ProductEvidenceReportService service,
            ProductIngredientEvidenceIngestionService ingredientEvidenceIngestionService
    ) {
        this.service = service;
        this.ingredientEvidenceIngestionService = ingredientEvidenceIngestionService;
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
        if (key == null || key.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        var opt = service.findActiveStatus(key, reason);

        if (opt.isEmpty()) {
            // ✅ IMPORTANT: 204 means “not reported” (not an error)
            return ResponseEntity.noContent().build();
        }

        var entity = opt.get();

        // ✅ Always return this shape (client decodes defensively)
        return ResponseEntity.ok(new ProductEvidenceStatusResponse(
                true,
                entity.getStatus(),
                true
        ));
    }

    public record ProductEvidenceReportResponse(
            Long id,
            boolean alreadyReported
    ) {}

    public record ProductEvidenceStatusResponse(
            boolean exists,
            String status,
            boolean alreadyReported
    ) {}

    public record ProductIngredientEvidenceResponse(
            Long id,
            boolean alreadyReported,
            String ocrStatus,
            int parsedIngredientCount,
            boolean reprocessQueued
    ) {}
}
