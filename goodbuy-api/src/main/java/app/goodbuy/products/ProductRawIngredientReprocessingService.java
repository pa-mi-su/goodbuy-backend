package app.goodbuy.products;

import app.goodbuy.adapters.core.products.model.ProductEntity;
import app.goodbuy.adapters.core.products.repo.ProductRepository;
import app.goodbuy.core.products.dto.ProductDetailDto;
import app.goodbuy.core.products.ingredients.IngredientTextParser;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class ProductRawIngredientReprocessingService {

    private final ProductRepository productRepository;
    private final AsyncProductIngestionService asyncProductIngestionService;

    public ProductRawIngredientReprocessingService(
            ProductRepository productRepository,
            AsyncProductIngestionService asyncProductIngestionService
    ) {
        this.productRepository = productRepository;
        this.asyncProductIngestionService = asyncProductIngestionService;
    }

    public boolean reprocessFromStoredRawText(String productEan) {
        if (productEan == null || productEan.isBlank()) {
            return false;
        }

        Optional<ProductEntity> opt = productRepository.findByEan(productEan.trim());
        if (opt.isEmpty()) {
            return false;
        }

        ProductEntity product = opt.get();
        List<String> parsedIngredients = IngredientTextParser.parse(product.getRawIngredientText());
        if (parsedIngredients.isEmpty()) {
            return false;
        }

        List<ProductDetailDto.IngredientDto> ingredients = parsedIngredients.stream()
                .map(label -> new ProductDetailDto.IngredientDto(
                        null,
                        label,
                        label,
                        Map.of(),
                        null,
                        null
                ))
                .toList();

        List<ProductDetailDto.ImageDto> images = firstImage(product)
                .map(url -> List.of(new ProductDetailDto.ImageDto(url, null, null)))
                .orElse(List.of());

        ProductDetailDto dto = new ProductDetailDto(
                product.getEan(),
                product.getName(),
                product.getBrand(),
                product.getCategory(),
                product.getDescription(),
                images,
                ingredients,
                Map.of(),
                Map.of(),
                "GOODBUY-RAW-TEXT",
                product.getDomain(),
                null,
                null
        );

        asyncProductIngestionService.enqueue(dto);
        return true;
    }

    private static Optional<String> firstImage(ProductEntity product) {
        if (product == null) {
            return Optional.empty();
        }
        String url = product.getPrimaryImageS3Url();
        if (url != null && !url.isBlank()) {
            return Optional.of(url);
        }
        url = product.getPrimaryImageUrl();
        return (url == null || url.isBlank()) ? Optional.empty() : Optional.of(url);
    }
}
