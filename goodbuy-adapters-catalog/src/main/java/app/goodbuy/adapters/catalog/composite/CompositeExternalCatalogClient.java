package app.goodbuy.adapters.catalog.composite;

import app.goodbuy.adapters.catalog.CatalogTransportException;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

public class CompositeExternalCatalogClient implements ExternalCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CompositeExternalCatalogClient.class);

    private final List<NamedClient> clients;

    public CompositeExternalCatalogClient(List<NamedClient> clients) {
        this.clients = clients == null ? List.of() : List.copyOf(clients);
    }

    @Override
    public Optional<ProductDetailDto> findByGtin(String gtin14) {
        CatalogTransportException lastTransport = null;

        for (NamedClient named : clients) {
            try {
                Optional<ProductDetailDto> result = named.client().findByGtin(gtin14);
                if (result.isPresent()) {
                    ProductDetailDto dto = result.get();
                    log.info("Composite catalog hit provider={} gtin14={} name={} brand={}",
                            named.name(), gtin14, safe(dto.name()), safe(dto.brand()));
                    return Optional.of(dto);
                }
                log.debug("Composite catalog miss provider={} gtin14={}", named.name(), gtin14);
            } catch (CatalogTransportException ex) {
                lastTransport = ex;
                log.warn("Composite catalog transport failure provider={} gtin14={} msg={}",
                        named.name(), gtin14, ex.getMessage());
            }
        }

        if (lastTransport != null) {
            throw lastTransport;
        }
        return Optional.empty();
    }

    public record NamedClient(String name, ExternalCatalogClient client) {}

    private static String safe(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
