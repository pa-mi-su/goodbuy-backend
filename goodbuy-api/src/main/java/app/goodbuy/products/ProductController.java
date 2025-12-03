package app.goodbuy.products;

import app.goodbuy.core.ingredients.dto.IngredientDTO;
import app.goodbuy.core.products.domain.ProductDomain;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ProductDomainResolverPort;
import app.goodbuy.ingredients.IngredientReadService;
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

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Product lookup + rating endpoint.
 *
 * Rules:
 *   - We only RATE products whose domain == CLEANING.
 *   - Other domains (food, baby, unknown, etc.) still return a ProductView, but
 *     with categorySupported=false and no GoodBuy ratings attached.
 *
 *   - NEW: For supported domains, we ONLY attach a product-level rating if we have
 *     coverage (a score) for ALL ingredients on the label. If we’re missing even
 *     one, product-level safetyScore/ratingLetter are null.
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

    public ProductController(ProductService service,
                             ObjectMapper objectMapper,
                             IngredientReadService ingredientReadService,
                             ProductDomainResolverPort productDomainResolver) {
        this.service = service;
        this.objectMapper = objectMapper;
        this.ingredientReadService = ingredientReadService;
        this.productDomainResolver = productDomainResolver;
    }

    // ─────────────────────────────────────────────────────────────────────
    // View models
    // ─────────────────────────────────────────────────────────────────────

    /**
     * Per-ingredient view for the product endpoint.
     * This is what iOS will use to color the leaf:
     *
     *  - name: display label used on the list
     *  - canonicalKey: our internal canonical key (if present)
     *  - inCatalog: true if we found it in GoodBuy DB
     *  - ratingLetter / safetyScore: used for coloring
     */
    public record IngredientView(
            String name,
            String canonicalKey,
            boolean inCatalog,
            String ratingLetter,
            BigDecimal safetyScore
    ) {}

    /**
     * High-level product view for iOS ResultView.
     *
     *  - domain: our coarse category ("cleaning", "baby", "food", "unknown", ...)
     *  - categorySupported:
     *       true  → we attach GoodBuy ratings (subject to coverage rules)
     *       false → we do NOT rate; client should show "not rated yet" UX
     *
     *  - safetyScore / ratingLetter at PRODUCT level:
     *       derived from ingredients, but ONLY when we have full coverage.
     */
    public record ProductView(
            String gtin,
            String name,
            String brand,
            String category,
            String domain,
            boolean categorySupported,
            String primaryImageUrl,
            List<String> images,
            List<IngredientView> ingredients,
            List<String> claims,
            List<String> hazards,
            String source,
            BigDecimal safetyScore,   // product-level score
            String ratingLetter       // product-level letter
    ) {
        static ProductView of(ProductDetailDto dto,
                              String source,
                              IngredientReadService ingredientReadService,
                              boolean categorySupported,
                              String domain) {

            // Flatten DTO images → list of URLs
            List<String> imageUrls = (dto.images() == null) ? List.of() :
                    dto.images().stream()
                            .map(ProductDetailDto.ImageDto::url)
                            .filter(Objects::nonNull)
                            .map(String::trim)
                            .filter(s -> !s.isEmpty())
                            .distinct()
                            .toList();

            String primaryImageUrl = imageUrls.isEmpty() ? null : imageUrls.get(0);

            // Build ingredient views + collect data for product-level score
            List<IngredientView> ingredientViews;

            if (dto.ingredients() == null) {
                ingredientViews = List.of();
            } else if (!categorySupported) {
                // Unsupported domain → labels only, no GoodBuy rating
                ingredientViews = dto.ingredients().stream()
                        .filter(Objects::nonNull)
                        .map(i -> {
                            String label = null;
                            if (i.original() != null && !i.original().isBlank()) {
                                label = i.original().trim();
                            } else if (i.canonical() != null && !i.canonical().isBlank()) {
                                label = i.canonical().trim();
                            } else if (i.id() != null && !i.id().isBlank()) {
                                label = i.id().trim();
                            }
                            if (label == null || label.isBlank()) {
                                return null;
                            }
                            return new IngredientView(
                                    label,
                                    null,
                                    false,
                                    null,
                                    null
                            );
                        })
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
            } else {
                // Supported domain → resolve & rate via GoodBuy DB
                ingredientViews = dto.ingredients().stream()
                        .filter(Objects::nonNull)
                        .map(i -> {
                            String label = null;
                            if (i.original() != null && !i.original().isBlank()) {
                                label = i.original().trim();
                            } else if (i.canonical() != null && !i.canonical().isBlank()) {
                                label = i.canonical().trim();
                            } else if (i.id() != null && !i.id().isBlank()) {
                                label = i.id().trim();
                            }

                            if (label == null || label.isBlank()) {
                                return null;
                            }

                            // Prefer canonical key for search if present, else fallback to label
                            String searchKey = (i.canonical() != null && !i.canonical().isBlank())
                                    ? i.canonical().trim()
                                    : label;

                            Optional<IngredientDTO> opt = ingredientReadService.searchRanked(searchKey);
                            if (opt.isPresent()) {
                                IngredientDTO ing = opt.get();
                                return new IngredientView(
                                        label,
                                        ing.canonicalKey(),
                                        true,
                                        ing.ratingLetter(),
                                        ing.safetyScore()
                                );
                            } else {
                                // Not in DB yet → white leaf
                                return new IngredientView(
                                        label,
                                        null,
                                        false,
                                        null,
                                        null
                                );
                            }
                        })
                        .filter(Objects::nonNull)
                        .distinct()
                        .toList();
            }

            // ── Product-level scoring with FULL COVERAGE requirement ──────────
            BigDecimal productScore = null;
            String productRating = null;

            if (categorySupported) {
                long totalIngredients = ingredientViews.size();
                long ratedIngredients = ingredientViews.stream()
                        .filter(IngredientView::inCatalog)
                        .filter(iv -> iv.safetyScore() != null)
                        .count();

                boolean fullCoverage = totalIngredients > 0 && ratedIngredients == totalIngredients;

                if (fullCoverage) {
                    // V1 product score: based on WORST (lowest) ingredient score we know about,
                    // but ONLY when we have data for all ingredients.
                    for (IngredientView iv : ingredientViews) {
                        if (!iv.inCatalog()) {
                            continue;
                        }
                        BigDecimal s = iv.safetyScore();
                        if (s == null) {
                            continue;
                        }
                        if (productScore == null || s.compareTo(productScore) < 0) {
                            productScore = s;
                            productRating = iv.ratingLetter();
                        }
                    }
                } else {
                    log.debug(
                            "ProductController: not assigning product-level rating for gtin={} (ratedIngredients={} totalIngredients={})",
                            dto.gtin(),
                            ratedIngredients,
                            totalIngredients
                    );
                }
            }

            return new ProductView(
                    dto.gtin(),
                    dto.name(),
                    dto.brand(),
                    dto.category(),
                    domain,
                    categorySupported,
                    primaryImageUrl,
                    imageUrls,
                    ingredientViews,
                    List.of(),
                    List.of(),
                    source,
                    productScore,
                    productRating
            );
        }
    }

    // ── Simple endpoint with ETag (used by iOS ResultView) ────────────────────

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

        ProductDetailDto dto = service.getByGtinOrNull(gtin14);
        if (dto == null) {
            log.warn("ProductController.getProduct: product not found gtin14={} source={}", gtin14, source);
            return buildError(HttpStatus.NOT_FOUND, "product_not_found", "Product not found in " + source, source);
        }

        // Domain gate via resolver (DB-driven rules behind a port).
        ProductDomain domainEnum = productDomainResolver.classify(
                dto.domain(),     // structured domain string from DB/upstream, if any
                dto.category(),
                dto.name(),
                dto.brand()
        );

        // Expose domain as lowercase string for API / headers
        String domain = domainEnum.name().toLowerCase(Locale.ROOT);

        // Only CLEANING is currently "supported" for rating.
        boolean categorySupported = (domainEnum == ProductDomain.CLEANING);
        if (!categorySupported) {
            log.info("ProductController.getProduct: domain_not_supported gtin14={} domain={}",
                    gtin14, domain);
        }

        ProductView view = ProductView.of(dto, source, ingredientReadService, categorySupported, domain);

        // Compute a stable weak ETag from the response body
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
                    .build();
        }

        log.info("served product gtin14={} name={} brand={} source={} domain={} supported={}",
                gtin14, safe(dto.name()), safe(dto.brand()), source, domain, categorySupported);

        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache().mustRevalidate())
                .eTag(etag)
                .header("X-Product-Source", source)
                .header("X-Product-Domain", domain)
                .header("X-Category-Supported", Boolean.toString(categorySupported))
                .body(view);
    }

    // ── Detail endpoint (full DTO) ────────────────────────────────────────────

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

        ProductDetailDto dto = service.getDetailByGtinOrNull(gtin14);
        if (dto == null) {
            log.warn("ProductController.getProductDetail: product detail not found gtin14={} source={}", gtin14, source);
            return buildError(HttpStatus.NOT_FOUND, "product_not_found", "Product not found in " + source, source);
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

    // ── Helpers ────────────────────────────────────────────────────────────────

    private static ResponseEntity<Map<String, Object>> buildError(
            HttpStatus status, String code, String message, String source
    ) {
        Map<String, Object> body = new HashMap<>();
        body.put("status", status.value());
        body.put("error", code);
        body.put("message", message);
        body.put("source", source);
        return ResponseEntity.status(status).body(body);
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
