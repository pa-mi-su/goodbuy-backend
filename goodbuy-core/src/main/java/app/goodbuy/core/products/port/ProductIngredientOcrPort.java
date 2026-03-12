package app.goodbuy.core.products.port;

import java.util.Optional;

public interface ProductIngredientOcrPort {

    Optional<ProductIngredientOcrResult> extract(ProductIngredientOcrRequest request);

    record ProductIngredientOcrRequest(
            String productEan,
            String productName,
            String brandName,
            byte[] frontImageBytes,
            String frontContentType,
            byte[] backImageBytes,
            String backContentType
    ) {}

    record ProductIngredientOcrResult(
            String rawText,
            String provider,
            String notes
    ) {}
}
