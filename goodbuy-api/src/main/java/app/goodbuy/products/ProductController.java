package app.goodbuy.products;

import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.products.domain.ProductDomain;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductDomainConfigPort;
import app.goodbuy.core.products.port.ProductDomainResolverPort;
import app.goodbuy.ingredients.IngredientReadService;
import app.goodbuy.products.view.ProductView;
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
import java.util.List;
import java.util.HashMap;
import java.util.Map;

/**
 * Product lookup + rating endpoint.
 *
 * Domain classification is DB-driven via product_domain_mapping (resolver).
 * Support/rating is DB-driven via product_domain_config (domainConfig).
 */
@Validated
@RestController
@RequestMapping(value = "/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductController {

    private static final Logger log = LoggerFactory.getLogger(ProductController.class);

    private final ProductService service;
    private final ObjectMapper objectMapper;
    private final IngredientReadService ingredientReadService;
    private final ProductDomainResolverPort productDomainResolver;
    private final ProductDomainConfigPort domainConfig;

    public ProductController(
            ProductService service,
            ObjectMapper objectMapper,
            IngredientReadService ingredientReadService,
            ProductDomainResolverPort productDomainResolver,
            ProductDomainConfigPort domainConfig
    ) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.ingredientReadService = ingredientReadService;
        this.productDomainResolver = productDomainResolver;
        this.domainConfig = domainConfig;
    }

    @GetMapping("/{code}")
    public ResponseEntity<?> getProduct(@PathVariable("code") String rawCode,
                                        WebRequest request) {
        final String source = service.activeSourceName();
        log.info("ProductController.getProduct: activeSource={}", source);

        final String gtin14;
        try {
            gtin14 = coerceToGtin14Or422(rawCode);
        } catch (ResponseStatusException e) {
            return buildError(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_barcode", e.getReason(), source);
        }

        final ProductDetailDto dto;
        try {
            dto = service.getByGtinOrNull(gtin14);
        } catch (ResponseStatusException rse) {
            // IMPORTANT: Distinguish catalog outage vs true miss.
            // ProductService should throw 503 when DB misses and external is unavailable.
            HttpStatus status = HttpStatus.valueOf(rse.getStatusCode().value());
            String msg = (rse.getReason() == null || rse.getReason().isBlank())
                    ? status.getReasonPhrase()
                    : rse.getReason();

            log.warn("ProductController.getProduct: upstream error gtin14={} status={} source={} msg={}",
                    gtin14, status.value(), source, msg);

            return ResponseEntity.status(status)
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .header("X-Product-Source", source)
                    .body(errorBody(status, status == HttpStatus.SERVICE_UNAVAILABLE ? "catalog_unavailable" : "error", msg, source));
        }

        if (dto == null) {
            log.info("ProductController.getProduct: product not found gtin14={} source={}", gtin14, source);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .header("X-Product-Source", source)
                    .body(errorBody(HttpStatus.NOT_FOUND, "product_not_found", "Product not found in " + source, source));
        }

        ProductDomain domainEnum = productDomainResolver.classify(
                dto.domain(),
                dto.category(),
                dto.name(),
                dto.brand()
        );

        String domain = (domainEnum == null ? ProductDomain.UNKNOWN.code() : domainEnum.code());
        boolean categorySupported = domainConfig.isEnabled(domain);
        boolean domainRated = domainConfig.isRated(domain);

        if (!categorySupported) {
            log.info("ProductController.getProduct: domain_not_supported gtin14={} domain={}", gtin14, domain);
        }

        List<IngredientDTO> immediateIngredientReads = categorySupported
                ? service.resolveImmediateIngredientReads(dto)
                : List.of();

        ProductView view = ProductView.of(
                dto,
                source,
                ingredientReadService,
                categorySupported,
                domain,
                immediateIngredientReads
        );

        String bodyJson;
        try {
            bodyJson = objectMapper.writeValueAsString(view);
        } catch (Exception ex) {
            log.warn("ETag serialization failed, serving without ETag. gtin14={}", gtin14, ex);
            return ResponseEntity.ok()
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .header("X-Product-Source", source)
                    .header("X-Product-Domain", domain)
                    .header("X-Category-Supported", Boolean.toString(categorySupported))
                    .header("X-Domain-Rated", Boolean.toString(domainRated))
                    .body(view);
        }

        String etag = "W/\"" + DigestUtils.sha256Hex(bodyJson.getBytes(StandardCharsets.UTF_8)).substring(0, 16) + "\"";

        if (request.checkNotModified(etag)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .eTag(etag)
                    .header("X-Product-Source", source)
                    .header("X-Product-Domain", domain)
                    .header("X-Category-Supported", Boolean.toString(categorySupported))
                    .header("X-Domain-Rated", Boolean.toString(domainRated))
                    .build();
        }

        log.info("served product gtin14={} name={} brand={} source={} domain={} supported={} rated={} ratingLetter={}",
                gtin14, safe(dto.name()), safe(dto.brand()), source, domain, categorySupported, domainRated, view.ratingLetter());

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .eTag(etag)
                .header("X-Product-Source", source)
                .header("X-Product-Domain", domain)
                .header("X-Category-Supported", Boolean.toString(categorySupported))
                .header("X-Domain-Rated", Boolean.toString(domainRated))
                .body(view);
    }

    @GetMapping("/{code}/detail")
    public ResponseEntity<?> getProductDetail(@PathVariable("code") String rawCode,
                                              WebRequest request) {
        final String source = service.activeSourceName();
        log.info("ProductController.getProductDetail: activeSource={}", source);

        final String gtin14;
        try {
            gtin14 = coerceToGtin14Or422(rawCode);
        } catch (ResponseStatusException e) {
            return buildError(HttpStatus.UNPROCESSABLE_ENTITY, "invalid_barcode", e.getReason(), source);
        }

        final ProductDetailDto dto;
        try {
            dto = service.getDetailByGtinOrNull(gtin14);
        } catch (ResponseStatusException rse) {
            HttpStatus status = HttpStatus.valueOf(rse.getStatusCode().value());
            String msg = (rse.getReason() == null || rse.getReason().isBlank())
                    ? status.getReasonPhrase()
                    : rse.getReason();

            log.warn("ProductController.getProductDetail: upstream error gtin14={} status={} source={} msg={}",
                    gtin14, status.value(), source, msg);

            return ResponseEntity.status(status)
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .header("X-Product-Source", source)
                    .body(errorBody(status, status == HttpStatus.SERVICE_UNAVAILABLE ? "catalog_unavailable" : "error", msg, source));
        }

        if (dto == null) {
            log.info("ProductController.getProductDetail: product detail not found gtin14={} source={}", gtin14, source);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .cacheControl(CacheControl.noCache().mustRevalidate())
                    .header("X-Product-Source", source)
                    .body(errorBody(HttpStatus.NOT_FOUND, "product_not_found", "Product not found in " + source, source));
        }

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

    private static ResponseEntity<Map<String, Object>> buildError(
            HttpStatus status, String code, String message, String source
    ) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .header("X-Product-Source", source)
                .body(errorBody(status, code, message, source));
    }

    private static Map<String, Object> errorBody(HttpStatus status, String code, String message, String source) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", code);
        body.put("message", message);
        body.put("source", source);
        return body;
    }

    private static String safe(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }

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
