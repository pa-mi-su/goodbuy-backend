package app.goodbuy.products;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@Validated
@RestController
@RequestMapping(value = "/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductController {

    private final ProductService service;
    private final BarcodeNormalizer normalizer;

    public ProductController(ProductService service, BarcodeNormalizer normalizer) {
        this.service = service;
        this.normalizer = normalizer;
    }

    @GetMapping("/{gtin}")
    public ProductDto getProduct(@PathVariable("gtin") String rawGtin) {
        // 1) Normalize & validate input → GTIN-14 (throws 422 on invalid)
        String gtin14 = normalizer.normalizeToGtin14OrThrow(rawGtin);

        // 2) Look up product
        ProductDto dto = service.getByGtinOrNull(gtin14);
        if (dto == null) {
            // Consistent 404 when not found
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
        }

        // 3) Return the product (JSON)
        return dto;
    }
}
