package app.goodbuy.products;

import app.goodbuy.adapters.core.products.model.MissingProductReportEntity;
import app.goodbuy.adapters.core.products.service.MissingProductReportService;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * REST endpoint for reporting missing products.
 *
 * iOS flow:
 *  - User scans a product that we don't have.
 *  - App POSTs here with EAN + basic context (name, brand, images, etc.).
 *  - We persist the report AND send a Slack notification.
 */
@RestController
@RequestMapping("/api/v1/products/missing")
public class MissingProductReportController {

    private static final Logger log =
            LoggerFactory.getLogger(MissingProductReportController.class);

    private final MissingProductReportService service;

    public MissingProductReportController(MissingProductReportService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MissingProductReportResponse reportMissing(
            @RequestBody MissingProductReportRequest req
    ) {
        MissingProductReportEntity saved = service.report(
                req.productEan(),
                req.productName(),
                req.brandName(),
                req.frontImageUrl(),
                req.backImageUrl(),
                req.appVersion(),
                req.platform(),
                req.notes()
        );

        log.info(
                "Missing product reported ean='{}' name='{}' brand='{}' platform={} version={}",
                req.productEan(), req.productName(), req.brandName(),
                req.platform(), req.appVersion()
        );

        return new MissingProductReportResponse(saved.getId());
    }

    // ─────────────────────────────────────────────
    // Request + Response DTOs
    // ─────────────────────────────────────────────

    public record MissingProductReportRequest(
            @NotBlank String productEan,
            @NotBlank String productName,
            String brandName,
            String frontImageUrl,
            String backImageUrl,
            String appVersion,
            String platform,
            String notes
    ) {}

    public record MissingProductReportResponse(Long id) {}
}
