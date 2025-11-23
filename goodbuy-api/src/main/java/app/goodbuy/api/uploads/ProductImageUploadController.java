package app.goodbuy.api.uploads;

import app.goodbuy.core.storage.ProductImageStoragePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/uploads")
public class ProductImageUploadController {

    private static final Logger log =
            LoggerFactory.getLogger(ProductImageUploadController.class);

    private final ProductImageStoragePort storagePort;

    public ProductImageUploadController(ProductImageStoragePort storagePort) {
        this.storagePort = storagePort;
    }

    /**
     * Upload a single product image to S3 and return its URL.
     *
     * iOS usage (example):
     *  - POST multipart/form-data to /api/v1/uploads/product-image
     *  - fields:
     *      - file: image data
     *      - ean: scanned product code
     *      - side: "front" | "back" | "other"
     *
     * Response:
     *  { "url": "https://bucket.s3.amazonaws.com/..." }
     */
    @PostMapping(
            path = "/product-image",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
            produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<Map<String, String>> uploadProductImage(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "ean", required = false) String ean,
            @RequestPart(value = "side", required = false) String side
    ) {
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "file must not be empty"));
        }

        String originalName = file.getOriginalFilename();
        String contentType = file.getContentType();
        if (!StringUtils.hasText(contentType)) {
            contentType = "image/jpeg"; // safe default
        }

        String safeEan = (ean == null || ean.isBlank()) ? "unknown" : ean.trim();
        String safeSide = (side == null || side.isBlank()) ? "unknown" : side.trim();

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
        String ext = extractExtension(originalName);
        String randomId = UUID.randomUUID().toString().replace("-", "");

        // Example key:
        // products/0647658062020/front/2025-11-19T19:20:01Z_abcd1234.jpg
        String key = "products/"
                + safeEan + "/"
                + safeSide + "/"
                + timestamp + "_" + randomId
                + ext;

        try {
            byte[] bytes = file.getBytes();
            String url = storagePort.uploadImage(key, bytes, contentType);

            log.info("ProductImageUploadController: uploaded ean={} side={} key={}", safeEan, safeSide, key);

            return ResponseEntity.ok(Map.of("url", url));
        } catch (Exception e) {
            log.warn("ProductImageUploadController: upload failed ean={} side={} err={}",
                    safeEan, safeSide, e.toString());

            return ResponseEntity.internalServerError()
                    .body(Map.of("error", "Failed to upload image"));
        }
    }

    private static String extractExtension(String filename) {
        if (!StringUtils.hasText(filename)) {
            return ".jpg";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return ".jpg";
        }
        String ext = filename.substring(dot).toLowerCase();
        // Very basic sanity check
        if (ext.length() > 10) {
            return ".jpg";
        }
        return ext;
    }
}
