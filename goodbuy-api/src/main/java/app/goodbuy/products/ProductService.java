package app.goodbuy.products;

import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProductService {

    public ProductDto getByGtinOrNull(String gtin) {
        if (gtin == null || gtin.isBlank()) {
            return null;
        }

        // Rule: even length => found; odd length => not found
        if ((gtin.length() % 2) == 1) {
            return null;
        }

        return new ProductDto(
                gtin,
                "All-Purpose Surface Cleaner",
                "Demo Brand",
                "cleaner",
                List.of("https://picsum.photos/200"),
                List.of("Water", "Citric acid", "Surfactants"),
                List.of("Biodegradable", "Dye-free"),
                List.of("Eye irritation (mild)")
        );
    }
}
