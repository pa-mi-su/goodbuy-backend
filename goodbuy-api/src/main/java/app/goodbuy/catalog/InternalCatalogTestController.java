package app.goodbuy.catalog;

import app.goodbuy.products.ProductDto;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping(value = "/internal/catalog", produces = MediaType.APPLICATION_JSON_VALUE)
@ConditionalOnBean(ExternalCatalogClient.class)
public class InternalCatalogTestController {

    private final ExternalCatalogClient client;

    public InternalCatalogTestController(ExternalCatalogClient client) {
        this.client = client;
    }

    @GetMapping("/{gtin14}")
    public ProductDto probe(@PathVariable String gtin14) {
        try {
            return client.findByGtin(gtin14)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "not found"));
        } catch (CatalogTransportException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "catalog_unavailable", e);
        }
    }
}
