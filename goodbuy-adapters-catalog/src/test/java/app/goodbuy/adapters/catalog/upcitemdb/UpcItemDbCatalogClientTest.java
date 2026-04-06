package app.goodbuy.adapters.catalog.upcitemdb;

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
import static org.junit.jupiter.api.Assertions.assertTrue;

class UpcItemDbCatalogClientTest {

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void mapsProductIdentityFromUpcItemDbResponse() throws Exception {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/lookup", jsonHandler("""
                {
                  "code": "OK",
                  "items": [
                    {
                      "title": "Vitamin C Gummies",
                      "brand": "Nature House",
                      "category": "Vitamins",
                      "description": "Orange flavored gummies",
                      "upc": "012345678905",
                      "images": [
                        "https://img.example/vitamin-front.jpg"
                      ]
                    }
                  ]
                }
                """));
        server.start();

        UpcItemDbCatalogClient client = new UpcItemDbCatalogClient(
                "http://localhost:" + server.getAddress().getPort() + "/lookup",
                "",
                "test-agent",
                1000,
                1000
        );

        Optional<ProductDetailDto> result = client.findByGtin("00012345678905");

        assertTrue(result.isPresent());
        assertEquals("Vitamin C Gummies", result.get().name());
        assertEquals("Nature House", result.get().brand());
        assertEquals("Vitamins", result.get().category());
        assertEquals("012345678905", result.get().gtin());
        assertEquals(1, result.get().images().size());
        assertEquals("UPCITEMDB", result.get().source());
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
