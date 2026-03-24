package app.goodbuy.enrichment;

import app.goodbuy.core.products.port.ProductEvidenceAnalyzerPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class OpenAiProductEvidenceAnalyzer implements ProductEvidenceAnalyzerPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProductEvidenceAnalyzer.class);

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final long readTimeoutMs;

    public OpenAiProductEvidenceAnalyzer(
            @Value("${goodbuy.product-analysis.openai.api-url:https://api.openai.com/v1/chat/completions}") String apiUrl,
            @Value("${goodbuy.product-analysis.openai.api-key:${OPENAI_API_KEY:}}") String apiKey,
            @Value("${goodbuy.product-analysis.openai.model:gpt-4.1-mini}") String model,
            @Value("${goodbuy.product-analysis.openai.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${goodbuy.product-analysis.openai.read-timeout-ms:6000}") long readTimeoutMs
    ) {
        this.apiUrl = apiUrl.trim();
        this.apiKey = apiKey.trim();
        this.model = model.trim();
        this.readTimeoutMs = readTimeoutMs;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
    }

    @Override
    public Optional<ProductEvidenceAnalysisResult> analyze(ProductEvidenceAnalysisRequest request) {
        if (request == null || apiKey.isBlank()) {
            return Optional.empty();
        }

        String text = safe(request.rawExtractedText());
        String manualIngredientText = safe(request.manualIngredientText());
        if (text.isBlank() && manualIngredientText.isBlank() && safe(request.hintedProductName()).isBlank()) {
            return Optional.empty();
        }

        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "model", model,
                    "response_format", Map.of(
                            "type", "json_schema",
                            "json_schema", Map.of(
                                    "name", "product_evidence_analysis",
                                    "strict", true,
                                    "schema", schema()
                            )
                    ),
                    "messages", List.of(
                            Map.of(
                                    "role", "system",
                                    "content", """
                                            You analyze failed retail-product scans for a consumer ingredient app.
                                            Infer a plausible product name, brand, broad domain, product category, and likely ingredient list.
                                            Use plain consumer-friendly labels.
                                            Return only valid JSON matching the schema.
                                            Confidence should reflect whether a backend should auto-create a draft product.
                                            Prefer conservative confidence when OCR text is sparse or noisy.
                                            Domains should be one of: food, vitamins, medicine, cleaning, personal-care, baby, household, other, unknown.
                                            """
                            ),
                            Map.of(
                                    "role", "user",
                                    "content", objectMapper.writeValueAsString(Map.of(
                                            "productEan", safe(request.productEan()),
                                            "reason", safe(request.reason()),
                                            "hintedProductName", safe(request.hintedProductName()),
                                            "hintedBrandName", safe(request.hintedBrandName()),
                                            "rawExtractedText", text,
                                            "manualIngredientText", manualIngredientText,
                                            "frontImageProvided", request.frontImageProvided(),
                                            "backImageProvided", request.backImageProvided()
                                    ))
                            )
                    )
            ));

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(apiUrl))
                    .timeout(Duration.ofMillis(readTimeoutMs))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();

            HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("OpenAiProductEvidenceAnalyzer failed status={} body={}", response.statusCode(), response.body());
                return Optional.empty();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode message = root.path("choices").path(0).path("message");
            JsonNode refusal = message.path("refusal");
            if (!refusal.isMissingNode() && !refusal.isNull() && !refusal.asText("").isBlank()) {
                log.warn("OpenAiProductEvidenceAnalyzer refusal={}", refusal.asText());
                return Optional.empty();
            }

            String content = extractContent(message.path("content"));
            if (content == null || content.isBlank()) {
                return Optional.empty();
            }

            JsonNode payload = objectMapper.readTree(content);
            return Optional.of(new ProductEvidenceAnalysisResult(
                    "OPENAI",
                    textOrNull(payload, "productName"),
                    textOrNull(payload, "brandName"),
                    textOrNull(payload, "domain"),
                    textOrNull(payload, "category"),
                    readList(payload.path("likelyIngredients")),
                    intOrNull(payload, "confidenceScore"),
                    textOrNull(payload, "confidenceBand"),
                    textOrNull(payload, "summary"),
                    content
            ));
        } catch (Exception ex) {
            log.warn("OpenAiProductEvidenceAnalyzer exception: {}", ex.toString());
            return Optional.empty();
        }
    }

    private Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.of(
                        "productName", stringSchema(),
                        "brandName", stringSchema(),
                        "domain", stringSchema(),
                        "category", stringSchema(),
                        "likelyIngredients", Map.of("type", "array", "items", stringSchema()),
                        "confidenceScore", Map.of("type", "integer", "minimum", 0, "maximum", 100),
                        "confidenceBand", stringSchema(),
                        "summary", stringSchema()
                ),
                "required", List.of(
                        "productName",
                        "brandName",
                        "domain",
                        "category",
                        "likelyIngredients",
                        "confidenceScore",
                        "confidenceBand",
                        "summary"
                )
        );
    }

    private static Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private static String extractContent(JsonNode contentNode) {
        if (contentNode == null || contentNode.isMissingNode() || contentNode.isNull()) {
            return null;
        }
        if (contentNode.isTextual()) {
            return contentNode.asText();
        }
        if (contentNode.isArray()) {
            for (JsonNode item : contentNode) {
                String text = item.path("text").asText(null);
                if (text != null && !text.isBlank()) {
                    return text;
                }
            }
        }
        return null;
    }

    private static List<String> readList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        return java.util.stream.StreamSupport.stream(node.spliterator(), false)
                .map(JsonNode::asText)
                .map(OpenAiProductEvidenceAnalyzer::safe)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isInt() ? value.asInt() : null;
    }

    private static String textOrNull(JsonNode node, String field) {
        return safe(node.path(field).asText(null));
    }

    private static String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
