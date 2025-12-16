package app.goodbuy.products;

import app.goodbuy.adapters.core.products.service.ProductEvidenceReportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/products/evidence")
public class ProductEvidenceReportController {

    private final ProductEvidenceReportService service;

    public ProductEvidenceReportController(ProductEvidenceReportService service) {
        this.service = service;
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

    public record ProductEvidenceReportResponse(
            Long id,
            boolean alreadyReported
    ) {}
}
