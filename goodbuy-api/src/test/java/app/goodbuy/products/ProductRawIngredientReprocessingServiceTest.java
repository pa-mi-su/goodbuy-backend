package app.goodbuy.products;

import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.repo.ProductRepository;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ProductRawIngredientReprocessingServiceTest {

    @Test
    void reprocessesStoredRawIngredientText() {
        ProductRepository productRepository = mock(ProductRepository.class);
        AsyncProductIngestionService asyncProductIngestionService = mock(AsyncProductIngestionService.class);

        ProductEntity product = new ProductEntity();
        product.setEan("00016500586579");
        product.setName("One A Day Prenatal Advanced Multivitamin");
        product.setBrand("One A Day");
        product.setCategory("vitamins");
        product.setDomain("vitamins");
        product.setRawIngredientText("Calcium Carbonate, Less than 2% of: silicon dioxide, titanium dioxide");

        when(productRepository.findByEan("00016500586579")).thenReturn(Optional.of(product));

        ProductRawIngredientReprocessingService service = new ProductRawIngredientReprocessingService(
                productRepository,
                asyncProductIngestionService
        );

        boolean queued = service.reprocessFromStoredRawText("00016500586579");

        assertTrue(queued);
        verify(asyncProductIngestionService).enqueue(any());
    }
}
