package app.goodbuy.products;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;

@Validated
@RestController
@RequestMapping(value = "/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService service;
    private final BarcodeNormalizer normalizer;

    public ProductController(ProductService service, BarcodeNormalizer normalizer) {
        this.service = service;
        this.normalizer = normalizer;
    }

    @GetMapping("/{gtin}")
    public ResponseEntity<ProductDto> getProduct(@PathVariable("gtin") String rawGtin) {
        // 1️⃣ Normalize & validate input → GTIN-14 (throws 422 on invalid)
        String gtin14 = normalizer.normalizeToGtin14OrThrow(rawGtin);

        // 2️⃣ Look up product
        ProductDto dto = service.getByGtinOrNull(gtin14);
        if (dto == null) {
            log.warn("product not found gtin14={}", gtin14);
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
        }

        // 3️⃣ App-level structured info log (MDC carries requestId automatically)
        log.info("served product gtin14={} name={} brand={}",
                gtin14, safe(dto.name()), safe(dto.brand()));

        // 4️⃣ Return JSON with short public cache headers
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .body(dto);
    }

    private static String safe(String s) {
        return s == null ? "—" : s;
    }
}
