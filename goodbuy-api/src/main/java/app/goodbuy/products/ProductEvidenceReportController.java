package app.goodbuy.products;

import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products/evidence")
public class ProductEvidenceReportController {

    private final ProductEvidenceReportService service;

    public ProductEvidenceReportController(ProductEvidenceReportService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ProductEvidenceReportResponse reportEvidence(@RequestBody ProductEvidenceReportRequest req) {

        var result = service.reportWithStatus(
                req.productEan(),
                req.reason(),
                req.productName(),
                req.brandName(),
                req.appVersion(),
                req.platform(),
                req.notes()
        );

        return new ProductEvidenceReportResponse(
                result.entity().getId(),
                !result.isNew()
        );
    }

    public record ProductEvidenceReportRequest(
            String productEan,
            String reason,
            String productName,
            String brandName,
            String appVersion,
            String platform,
            String notes
    ) {}

    public record ProductEvidenceReportResponse(
            Long reportId,
            boolean alreadyReported
    ) {}
}
