package app.goodbuy.products;

import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(value = "/v1/products", produces = MediaType.APPLICATION_JSON_VALUE)
public class ProductController {

    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/{gtin}")
    public ProductDto getProduct(
            @PathVariable
            @Pattern(regexp = "^[0-9A-Za-z-_.]{5,64}$", message = "Invalid GTIN format")
            String gtin
    ) {
        ProductDto dto = service.getByGtinOrNull(gtin);
        if (dto == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "not found");
        }
        return dto;
    }
}
