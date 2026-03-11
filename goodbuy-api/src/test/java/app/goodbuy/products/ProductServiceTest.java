package app.goodbuy.products;

import app.goodbuy.core.products.StrictProductIngestionException;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.port.ExternalCatalogClient;
import app.goodbuy.core.products.port.ProductLookupPort;
import app.goodbuy.core.products.port.ProductSnapshotPort;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductServiceTest {

    @Test
    void returnsDbSnapshotWhenAlreadyStrictlyScored() {
        ProductLookupPort lookup = mock(ProductLookupPort.class);
        ProductDetailDto scored = dto("00012345678901", new BigDecimal("91.00"), "A");

        when(lookup.findByGtin("00012345678901")).thenReturn(Optional.of(scored));

        ProductService service = new ProductService(Optional.empty(), Optional.of(lookup), Optional.empty());

        ProductDetailDto result = service.getByGtinOrNull("00012345678901");

        assertEquals("A", result.ratingLetter());
    }

    @Test
    void refusesToReturnExistingUnscoredProductWhenExternalMisses() {
        ProductLookupPort lookup = mock(ProductLookupPort.class);
        ExternalCatalogClient external = mock(ExternalCatalogClient.class);

        when(lookup.findByGtin("00012345678901")).thenReturn(Optional.of(dto("00012345678901", null, null)));
        when(external.findByGtin("00012345678901")).thenReturn(Optional.empty());

        ProductService service = new ProductService(Optional.of(external), Optional.of(lookup), Optional.empty());

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getByGtinOrNull("00012345678901"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
    }

    @Test
    void propagatesStrictSnapshotFailureAsServiceUnavailable() {
        ProductLookupPort lookup = mock(ProductLookupPort.class);
        ExternalCatalogClient external = mock(ExternalCatalogClient.class);
        ProductSnapshotPort snapshot = mock(ProductSnapshotPort.class);
        ProductDetailDto externalDto = dto("00012345678901", null, null);

        when(lookup.findByGtin("00012345678901")).thenReturn(Optional.empty());
        when(external.findByGtin("00012345678901")).thenReturn(Optional.of(externalDto));
        doThrow(new StrictProductIngestionException("ingredient incomplete")).when(snapshot).saveSnapshot(externalDto);

        ProductService service = new ProductService(Optional.of(external), Optional.of(lookup), Optional.of(snapshot));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.getByGtinOrNull("00012345678901"));

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ex.getStatusCode());
        verify(snapshot).saveSnapshot(externalDto);
    }

    private static ProductDetailDto dto(String gtin, BigDecimal safetyScore, String ratingLetter) {
        return new ProductDetailDto(
                gtin,
                "Demo Product",
                "Brand",
                "Cleaning",
                null,
                List.of(),
                List.of(),
                Map.of(),
                Map.of(),
                "GOODBUY-DB",
                "cleaning",
                safetyScore,
                ratingLetter
        );
    }
}
