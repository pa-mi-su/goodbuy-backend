package app.goodbuy.products;

import app.goodbuy.core.products.dto.ProductDetailDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.digest.DigestUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Validated
@RestController
@RequestMapping(value = "/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);
    private final ProductService service;
    private final ObjectMapper objectMapper;                        // ⬅ add

    public ProductController(ProductService service, ObjectMapper objectMapper) {
        this.service = service;
        this.objectMapper = objectMapper;                           // ⬅ add
    }

    public record ProductView(
            String gtin,
            String name,
            String brand,
            String category,
            List<String> images,
            List<String> ingredients,
            List<String> claims,
            List<String> hazards,
            String source
    ) {
        static ProductView of(ProductDetailDto dto, String source) {
            List<String> imageUrls = (dto.images() == null) ? List.of() :
                    dto.images().stream()
                            .map(ProductDetailDto.ImageDto::url)
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .toList();

            List<String> ingredientNames = (dto.ingredients() == null) ? List.of() :
                    dto.ingredients().stream()
                            .filter(Objects::nonNull)
                            .map(i -> {
                                if (i.original() != null && !i.original().isBlank()) return i.original().trim();
                                if (i.canonical() != null && !i.canonical().isBlank()) return i.canonical().trim();
                                if (i.id() != null && !i.id().isBlank()) return i.id().trim();
                                return null;
                            })
                            .filter(Objects::nonNull)
                            .distinct()
                            .toList();

            return new ProductView(
                    dto.gtin(),
                    dto.name(),
                    dto.brand(),
                    dto.category(),
                    imageUrls,
                    ingredientNames,
                    List.of(),
                    List.of(),
                    source
            );
        }
    }

    // ── ETag-enabled simple endpoint ───────────────────────────────────────────
    @GetMapping("/{code}")
    public ResponseEntity<?> getProduct(@PathVariable("code") String rawCode,
                                        WebRequest request) {                     // ⬅ add WebRequest
        final String source = service.activeSourceName();
        log.info("ProductController.getProduct: activeSource={}", source);

        final String gtin14;
        try {
            gtin14 = coerceToGtin14Or422(rawCode);
        } catch (ResponseStatusException e) {
            return buildError(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_barcode", e.getReason(), source);
        }

        ProductDetailDto dto = service.getByGtinOrNull(gtin14);
        if (dto == null) {
            log.warn("ProductController.getProduct: product not found gtin14={} source={}", gtin14, source);
            return buildError(HttpStatus.NOT_FOUND, "product_not_found", "Product not found in " + source, source);
        }

        ProductView view = ProductView.of(dto, source);

        // Compute a stable weak ETag from the response body
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(view);
        } catch (Exception ex) {
            // Fallback: still serve without ETag if serialization failed here (shouldn’t happen)
            log.warn("ETag serialization failed, serving without ETag. gtin14={}", gtin14, ex);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache().mustRevalidate())         // ⬅ force revalidation
                    .header("X-Product-Source", source)
                    .body(view);
        }
        String etag = "W/\"" + DigestUtils.sha256Hex(bodyJson.getBytes(StandardCharsets.UTF_8)).substring(0, 16) + "\"";

        // If-None-Match handling → 304
        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .eTag(etag)
                    .header("X-Product-Source", source)
                    .build();
        }

        log.info("served product gtin14={} name={} brand={} source={}",
                gtin14, safe(dto.name()), safe(dto.brand()), source);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate())             // ⬅ store-allowed, must revalidate
                .eTag(etag)
                .header("X-Product-Source", source)
                .body(view);
    }

    // ── ETag-enabled detail endpoint ───────────────────────────────────────────
    @GetMapping("/{code}/detail")
    public ResponseEntity<?> getProductDetail(@PathVariable("code") String rawCode,
                                              WebRequest request) {               // ⬅ add WebRequest
        final String source = service.activeSourceName();
        log.info("ProductController.getProductDetail: activeSource={}", source);

        final String gtin14;
        try {
            gtin14 = coerceToGtin14Or422(rawCode);
        } catch (ResponseStatusException e) {
            return buildError(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_barcode", e.getReason(), source);
        }

        ProductDetailDto dto = service.getDetailByGtinOrNull(gtin14);
        if (dto == null) {
            log.warn("ProductController.getProductDetail: product detail not found gtin14={} source={}", gtin14, source);
            return buildError(HttpStatus.NOT_FOUND, "product_not_found", "Product not found in " + source, source);
        }

        // Build ETag from the raw DTO (detail payload)
        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(dto);
        } catch (Exception ex) {
            log.warn("ETag serialization failed (detail), serving without ETag. gtin14={}", gtin14, ex);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .header("X-Product-Source", dto.source() == null ? source : dto.source())
                    .body(dto);
        }
        String etag = "W/\"" + DigestUtils.sha256Hex(bodyJson.getBytes(StandardCharsets.UTF_8)).substring(0, 16) + "\"";

        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .eTag(etag)
                    .header("X-Product-Source", dto.source() == null ? source : dto.source())
                    .build();
        }

        log.info("served product DETAIL gtin14={} name={} brand={} source={}",
                gtin14, safe(dto.name()), safe(dto.brand()), dto.source());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .eTag(etag)
                .header("X-Product-Source", dto.source() == null ? source : dto.source())
                .body(dto);
    }

    // ── Helpers (unchanged) ────────────────────────────────────────────────────
    private static ResponseEntity<Map<String, Object>> buildError(HttpStatus status, String code, String message, String source) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", code);
        body.put("message", message);
        body.put("source", source);
        return ResponseEntity.status(status).body(body);
    }

    private static String safe(String s) { return (s == null || s.isBlank()) ? "—" : s; }

    private static String coerceToGtin14Or422(String raw) {
        if (raw == null) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_barcode: null");
        }
        String digits = raw.trim().replaceAll("\\s+", "");
        if (!digits.matches("\\d+")) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_barcode: non_digits");
        }
        return switch (digits.length()) {
            case 12 -> "00" + digits;
            case 13 -> "0" + digits;
            case 14 -> digits;
            default -> throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_barcode: length");
        };
    }
}
