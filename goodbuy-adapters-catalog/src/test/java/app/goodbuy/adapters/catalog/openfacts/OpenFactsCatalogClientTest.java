package app.goodbuy.adapters.catalog.openfacts;

import app.goodbuy.core.products.dto.ProductDetailDto;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenFactsCatalogClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsStructuredIngredientsFromOpenFactsResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v2/product/00012345678901", jsonHandler("""
                {
                  "status": 1,
                  "product": {
                    "code": "00012345678901",
                    "product_name": "Gentle Baby Wash",
                    "brands": "Johnson's, ACME",
                    "categories": "Personal care, Baby",
                    "generic_name": "Wash and shampoo",
                    "image_front_url": "https://img.example/front.jpg",
                    "ingredients": [
                      { "id": "en:water", "text": "Water" },
                      { "id": "en:glycerin", "text": "Glycerin", "vegan": "yes", "vegetarian": "yes" }
                    ]
                  }
                }
                """));
        server.start();

        OpenFactsCatalogClient client = new OpenFactsCatalogClient(
                "http://localhost:" + server.getAddress().getPort(),
                "OPEN-BEAUTY-FACTS",
                "personal-care",
                "test-agent",
                1000,
                1000
        );

        Optional<ProductDetailDto> result = client.findByGtin("00012345678901");

        assertTrue(result.isPresent());
        assertEquals("Gentle Baby Wash", result.get().name());
        assertEquals("Johnson's", result.get().brand());
        assertEquals("Personal care", result.get().category());
        assertEquals("personal-care", result.get().domain());
        assertEquals(2, result.get().ingredients().size());
        assertEquals("water", result.get().ingredients().get(0).id());
        assertEquals("Glycerin", result.get().ingredients().get(1).original());
        assertEquals(Boolean.TRUE, result.get().ingredients().get(1).isVegan());
    }

    @Test
    void fallsBackToIngredientsTextWhenStructuredIngredientsMissing() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v2/product/0123456789012", jsonHandler("""
                {
                  "status": 1,
                  "product": {
                    "code": "0123456789012",
                    "product_name_en": "Dish Soap",
                    "brands": "GoodBuy Home",
                    "ingredients_text_en": "Water, Sodium Laureth Sulfate, Fragrance"
                  }
                }
                """));
        server.start();

        OpenFactsCatalogClient client = new OpenFactsCatalogClient(
                "http://localhost:" + server.getAddress().getPort(),
                "OPEN-PRODUCTS-FACTS",
                "household",
                "test-agent",
                1000,
                1000
        );

        Optional<ProductDetailDto> result = client.findByGtin("0123456789012");

        assertTrue(result.isPresent());
        assertEquals(3, result.get().ingredients().size());
        assertEquals("Water", result.get().ingredients().get(0).original());
        assertEquals("Fragrance", result.get().ingredients().get(2).canonical());
    }

    @Test
    void returnsEmptyWhenOpenFactsReportsNoProduct() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/api/v2/product/00000000000000", jsonHandler("""
                {
                  "status": 0
                }
                """));
        server.start();

        OpenFactsCatalogClient client = new OpenFactsCatalogClient(
                "http://localhost:" + server.getAddress().getPort(),
                "OPEN-FOOD-FACTS",
                "food",
                "test-agent",
                1000,
                1000
        );

        Optional<ProductDetailDto> result = client.findByGtin("00000000000000");

        assertFalse(result.isPresent());
    }

    private static HttpHandler jsonHandler(String body) {
        return exchange -> respond(exchange, 200, body);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
