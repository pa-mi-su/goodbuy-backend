package app.goodbuy.adapters.catalog.eandb;

import app.goodbuy.core.products.dto.ProductDetailDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class EanDbCatalogClientTest {

    private final ObjectMapper om = new ObjectMapper();

    @Test
    void dropsStandaloneLessThanHeader() throws Exception {
        var node = om.readTree("""
                {
                  "originalNames": { "en": "Less than 2% of:" },
                  "canonicalNames": { "en": "Less than 2% of:" }
                }
                """);

        List<ProductDetailDto.IngredientDto> result = EanDbCatalogClient.mapIngredientNode(node);

        assertEquals(0, result.size());
    }

    @Test
    void expandsLessThanPrefixIntoRealIngredients() throws Exception {
        var node = om.readTree("""
                {
                  "originalNames": { "en": "Less than 2% of: silicon dioxide, titanium dioxide, red 40 lake" },
                  "canonicalNames": { "en": "Less than 2% of: silicon dioxide, titanium dioxide, red 40 lake" }
                }
                """);

        List<ProductDetailDto.IngredientDto> result = EanDbCatalogClient.mapIngredientNode(node);

        assertEquals(3, result.size());
        assertEquals("silicon dioxide", result.get(0).original());
        assertEquals("titanium dioxide", result.get(1).original());
        assertEquals("red 40 lake", result.get(2).original());
        assertNull(result.get(0).id());
    }

    @Test
    void preservesNormalIngredientLabels() {
        List<String> result = EanDbCatalogClient.expandIngredientLabels("Calcium Carbonate", null);

        assertEquals(List.of("Calcium Carbonate"), result);
    }
}
