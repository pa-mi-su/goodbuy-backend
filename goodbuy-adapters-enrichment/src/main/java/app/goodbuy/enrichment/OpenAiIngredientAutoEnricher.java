package app.goodbuy.enrichment;

import app.goodbuy.core.ingredients.port.IngredientAutoEnricherPort;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentRequest;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Component
@Primary
public class OpenAiIngredientAutoEnricher implements IngredientAutoEnricherPort {

    private static final Logger log = LoggerFactory.getLogger(OpenAiIngredientAutoEnricher.class);
    private static final String PROVIDER = "OPENAI";

    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient httpClient;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final PubChemIngredientAutoEnricher pubChem;
    private final int maxAttempts;
    private final long retryDelayMs;

    public OpenAiIngredientAutoEnricher(
            Optional<PubChemIngredientAutoEnricher> pubChem,
            @Value("${goodbuy.ingredients.auto-enrich.openai.api-url:https://api.openai.com/v1/chat/completions}") String apiUrl,
            @Value("${goodbuy.ingredients.auto-enrich.openai.api-key:}") String apiKey,
            @Value("${goodbuy.ingredients.auto-enrich.openai.model:gpt-4.1-mini}") String model,
            @Value("${goodbuy.ingredients.auto-enrich.openai.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${goodbuy.ingredients.auto-enrich.openai.read-timeout-ms:8000}") long readTimeoutMs,
            @Value("${goodbuy.ingredients.auto-enrich.openai.max-attempts:3}") int maxAttempts,
            @Value("${goodbuy.ingredients.auto-enrich.openai.retry-delay-ms:750}") long retryDelayMs
    ) {
        this.pubChem = pubChem.orElse(null);
        this.apiUrl = apiUrl.trim();
        this.apiKey = apiKey.trim();
        this.model = model.trim();
        this.maxAttempts = Math.max(1, maxAttempts);
        this.retryDelayMs = Math.max(0, retryDelayMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        log.info("OpenAiIngredientAutoEnricher configured model={} apiUrl={} pubChemSupport={} maxAttempts={} retryDelayMs={}",
                this.model, this.apiUrl, this.pubChem != null, this.maxAttempts, this.retryDelayMs);
        this.readTimeout = readTimeoutMs;
    }

    private final long readTimeout;

    @Override
    public IngredientEnrichmentResult enrich(IngredientEnrichmentRequest req) {
        if (req == null || isBlank(req.canonicalKey())) {
            return notEnriched("request_invalid");
        }
        if (apiKey.isBlank()) {
            throw new IllegalStateException("OpenAI API key is not configured");
        }

        IngredientEnrichmentResult pubChemResult = null;
        if (pubChem != null) {
            try {
                pubChemResult = pubChem.enrich(req);
            } catch (Exception ex) {
                log.warn("OpenAiIngredientAutoEnricher: PubChem prefetch failed canonicalKey='{}': {}",
                        req.canonicalKey(), ex.toString());
            }
        }

        String lastFailureNote = "openai_unknown_failure";
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String responseJson = callOpenAi(req, pubChemResult, attempt);
                JsonNode payload = om.readTree(responseJson);
                IngredientEnrichmentResult merged = merge(req, parsePayload(payload), pubChemResult);
                String incompleteReason = completenessFailure(merged);
                if (incompleteReason == null) {
                    return merged;
                }

                lastFailureNote = "openai_incomplete_profile_" + incompleteReason;
                log.warn("OpenAiIngredientAutoEnricher incomplete canonicalKey='{}' attempt={}/{} reason={}",
                        req.canonicalKey(), attempt, maxAttempts, incompleteReason);
                if (attempt < maxAttempts) {
                    pauseBeforeRetry();
                }
            } catch (Exception ex) {
                lastFailureNote = "openai_exception_" + ex.getClass().getSimpleName();
                log.warn("OpenAiIngredientAutoEnricher failed canonicalKey='{}' attempt={}/{}: {}",
                        req.canonicalKey(), attempt, maxAttempts, ex.toString());
                if (attempt < maxAttempts && isRetryable(ex)) {
                    pauseBeforeRetry();
                    continue;
                }
                break;
            }
        }
        return notEnriched(lastFailureNote);
    }

    private String callOpenAi(IngredientEnrichmentRequest req, IngredientEnrichmentResult pubChemResult, int attempt) throws Exception {
        String query = firstNonBlank(req.displayName(), req.canonicalKey());
        String requestBody = om.writeValueAsString(Map.of(
                "model", model,
                "response_format", Map.of(
                        "type", "json_schema",
                        "json_schema", Map.of(
                                "name", "ingredient_enrichment",
                                "strict", true,
                                "schema", schema()
                        )
                ),
                "messages", List.of(
                        Map.of(
                                "role", "system",
                                "content", """
                                        You enrich retail product ingredients for a consumer safety database.
                                        Return only valid JSON matching the schema.
                                        Fill every field.
                                        Write for a normal consumer, not a chemist or regulator.
                                        Use short, plain-English sentences with minimal jargon.
                                        Make displayName the label a shopper would best recognize.
                                        Make summary a one-sentence answer to: "What is this ingredient?"
                                        Make description explain what it is and where it is commonly used.
                                        Make functionUse explain what it does in the product in plain language.
                                        Make concerns explain what it can do to a person or why someone might care.
                                        Make category human-readable, such as preservative, fragrance ingredient, solvent, color additive, plant extract, surfactant, vitamin, or mineral.
                                        Make regulationNotes understandable to a consumer; do not write like a lawyer.
                                        Prefer concrete everyday examples when helpful.
                                        Do not hedge with generic filler like "more research is needed" unless truly necessary.
                                        If evidence is weak, still provide the best supported normalized value instead of leaving fields blank.
                                        Boolean risk fields must always be present.
                                        """
                        ),
                        Map.of(
                                "role", "user",
                                "content", buildUserPrompt(req, query, pubChemResult)
                        )
                )
        ));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .timeout(Duration.ofMillis(readTimeout))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new IllegalStateException("OpenAI HTTP " + response.statusCode() + ": " + response.body());
        }

        JsonNode root = om.readTree(response.body());
        JsonNode message = root.path("choices").path(0).path("message");
        JsonNode refusal = message.path("refusal");
        if (!refusal.isMissingNode() && !refusal.isNull() && !refusal.asText("").isBlank()) {
            throw new IllegalStateException("OpenAI refusal: " + refusal.asText());
        }
        String contentText = extractContentText(message.path("content"));
        if (contentText == null || contentText.isBlank()) {
            throw new IllegalStateException("OpenAI response did not contain usable message content on attempt " + attempt);
        }
        return contentText;
    }

    private IngredientEnrichmentResult parsePayload(JsonNode payload) {
        List<String> aliases = readList(payload.path("aliases"));
        List<String> tags = readList(payload.path("tags"));

        return new IngredientEnrichmentResult(
                true,
                textOrNull(payload, "displayName"),
                textOrNull(payload, "summary"),
                textOrNull(payload, "description"),
                textOrNull(payload, "functionUse"),
                textOrNull(payload, "concerns"),
                textOrNull(payload, "category"),
                textOrNull(payload, "regulationNotes"),
                intOrNull(payload, "referencesCount"),
                aliases,
                tags,
                readList(payload.path("sourceUrls")),
                PROVIDER,
                "OpenAI ingredient enrichment",
                "model=" + model,
                intOrNull(payload, "iarcGroup"),
                boolOrNull(payload, "prop65Listed"),
                intOrNull(payload, "ewgScore"),
                boolOrNull(payload, "euProhibited"),
                boolOrNull(payload, "euRestricted"),
                boolOrNull(payload, "pubchemMutagen"),
                boolOrNull(payload, "pubchemReproductiveToxin"),
                boolOrNull(payload, "epaChronicToxicity"),
                boolOrNull(payload, "skinIrritant")
        );
    }

    private IngredientEnrichmentResult merge(
            IngredientEnrichmentRequest req,
            IngredientEnrichmentResult openAi,
            IngredientEnrichmentResult pubChemResult
    ) {
        Set<String> sourceUrls = new LinkedHashSet<>();
        sourceUrls.addAll(safeList(openAi.sourceUrls()));
        if (pubChemResult != null) {
            sourceUrls.addAll(safeList(pubChemResult.sourceUrls()));
        }

        int referenceCount = Math.max(
                openAi.referencesCount() == null ? 0 : openAi.referencesCount(),
                sourceUrls.size()
        );

        return new IngredientEnrichmentResult(
                true,
                firstNonBlank(openAi.displayName(), pubChemResult == null ? null : pubChemResult.displayName(), req.displayName(), req.canonicalKey()),
                firstNonBlank(openAi.summary(), pubChemResult == null ? null : pubChemResult.summary()),
                firstNonBlank(openAi.description(), pubChemResult == null ? null : pubChemResult.description(), openAi.summary()),
                firstNonBlank(openAi.functionUse()),
                firstNonBlank(openAi.concerns()),
                firstNonBlank(openAi.category(), pubChemResult == null ? null : pubChemResult.category(), "compound"),
                firstNonBlank(openAi.regulationNotes()),
                referenceCount,
                dedupe(openAi.aliases()),
                dedupe(openAi.tags()),
                new ArrayList<>(sourceUrls),
                PROVIDER,
                "OpenAI ingredient enrichment",
                pubChemResult != null && !isBlank(pubChemResult.note())
                        ? "model=" + model + ";" + pubChemResult.note()
                        : "model=" + model,
                null,
                null,
                null,
                null,
                null,
                pubChemResult == null ? null : pubChemResult.pubchemMutagen(),
                pubChemResult == null ? null : pubChemResult.pubchemReproductiveToxin(),
                null,
                null
        );
    }

    private String completenessFailure(IngredientEnrichmentResult result) {
        if (result == null) return "result_null";
        if (!result.enriched()) return "enriched_false";
        if (isBlank(result.displayName())) return "display_name";
        if (isBlank(result.summary())) return "summary";
        if (isBlank(result.description())) return "description";
        if (isBlank(result.functionUse())) return "function_use";
        if (isBlank(result.concerns())) return "concerns";
        if (isBlank(result.category())) return "category";
        if (isBlank(result.regulationNotes())) return "regulation_notes";
        if (result.referencesCount() == null || result.referencesCount() < 1) return "references_count";
        if (safeList(result.sourceUrls()).isEmpty()) return "source_urls";
        return null;
    }

    private String buildUserPrompt(
            IngredientEnrichmentRequest req,
            String query,
            IngredientEnrichmentResult pubChemResult
    ) throws Exception {
        java.util.LinkedHashMap<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("canonicalKey", req.canonicalKey());
        payload.put("displayName", req.displayName());
        payload.put("productEan", req.productEan());
        payload.put("sourceProvider", req.sourceProvider());
        payload.put("externalIds", req.externalIds() == null ? Map.of() : req.externalIds());
        payload.put("query", query);
        payload.put("writingGoals", List.of(
                "Explain the ingredient in plain English.",
                "Help a shopper understand what it is, what it does, and why it matters.",
                "Prefer clear everyday wording over technical jargon.",
                "Include common real-world product uses when known."
        ));

        java.util.LinkedHashMap<String, Object> pubChemPayload = new java.util.LinkedHashMap<>();
        if (pubChemResult != null) {
            pubChemPayload.put("displayName", pubChemResult.displayName());
            pubChemPayload.put("summary", pubChemResult.summary());
            pubChemPayload.put("category", pubChemResult.category());
            pubChemPayload.put("sourceUrls", safeList(pubChemResult.sourceUrls()));
            pubChemPayload.put("note", pubChemResult.note());
            pubChemPayload.put("pubchemMutagen", pubChemResult.pubchemMutagen());
            pubChemPayload.put("pubchemReproductiveToxin", pubChemResult.pubchemReproductiveToxin());
        }
        payload.put("pubChem", pubChemPayload);

        return om.writeValueAsString(payload);
    }

    private Map<String, Object> schema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "properties", Map.ofEntries(
                        Map.entry("displayName", stringSchema()),
                        Map.entry("summary", stringSchema()),
                        Map.entry("description", stringSchema()),
                        Map.entry("functionUse", stringSchema()),
                        Map.entry("concerns", stringSchema()),
                        Map.entry("category", stringSchema()),
                        Map.entry("regulationNotes", stringSchema()),
                        Map.entry("referencesCount", Map.of("type", "integer", "minimum", 0)),
                        Map.entry("aliases", Map.of("type", "array", "items", stringSchema())),
                        Map.entry("tags", Map.of("type", "array", "items", stringSchema())),
                        Map.entry("sourceUrls", Map.of("type", "array", "items", stringSchema())),
                        Map.entry("iarcGroup", nullableIntegerSchema()),
                        Map.entry("prop65Listed", nullableBooleanSchema()),
                        Map.entry("ewgScore", nullableIntegerSchema()),
                        Map.entry("euProhibited", nullableBooleanSchema()),
                        Map.entry("euRestricted", nullableBooleanSchema()),
                        Map.entry("pubchemMutagen", nullableBooleanSchema()),
                        Map.entry("pubchemReproductiveToxin", nullableBooleanSchema()),
                        Map.entry("epaChronicToxicity", nullableBooleanSchema()),
                        Map.entry("skinIrritant", nullableBooleanSchema())
                ),
                "required", List.of(
                        "displayName",
                        "summary",
                        "description",
                        "functionUse",
                        "concerns",
                        "category",
                        "regulationNotes",
                        "referencesCount",
                        "aliases",
                        "tags",
                        "sourceUrls",
                        "iarcGroup",
                        "prop65Listed",
                        "ewgScore",
                        "euProhibited",
                        "euRestricted",
                        "pubchemMutagen",
                        "pubchemReproductiveToxin",
                        "epaChronicToxicity",
                        "skinIrritant"
                )
        );
    }

    private static Map<String, Object> stringSchema() {
        return Map.of("type", "string");
    }

    private static Map<String, Object> nullableIntegerSchema() {
        return Map.of("type", List.of("integer", "null"));
    }

    private static Map<String, Object> nullableBooleanSchema() {
        return Map.of("type", List.of("boolean", "null"));
    }

    private static String extractContentText(JsonNode content) {
        if (content == null || content.isMissingNode() || content.isNull()) return null;
        if (content.isTextual()) {
            String value = content.asText().trim();
            return value.isEmpty() ? null : value;
        }
        if (content.isArray()) {
            StringBuilder out = new StringBuilder();
            content.forEach(part -> {
                String type = part.path("type").asText("");
                if ("text".equals(type)) {
                    String text = part.path("text").asText("").trim();
                    if (!text.isEmpty()) {
                        if (out.length() > 0) out.append('\n');
                        out.append(text);
                    }
                }
            });
            return out.isEmpty() ? null : out.toString();
        }
        return null;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        if (!child.isTextual()) return null;
        String value = child.asText().trim();
        return value.isEmpty() ? null : value;
    }

    private static Integer intOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isInt() ? child.asInt() : null;
    }

    private static Boolean boolOrNull(JsonNode node, String field) {
        JsonNode child = node.path(field);
        return child.isBoolean() ? child.asBoolean() : null;
    }

    private static List<String> readList(JsonNode node) {
        if (node == null || !node.isArray()) return List.of();
        List<String> values = new ArrayList<>();
        node.forEach(child -> {
            if (child.isTextual()) {
                String value = child.asText().trim();
                if (!value.isEmpty()) values.add(value);
            }
        });
        return values;
    }

    private static List<String> dedupe(List<String> values) {
        LinkedHashSet<String> out = new LinkedHashSet<>();
        safeList(values).stream()
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .forEach(out::add);
        return new ArrayList<>(out);
    }

    private static <T> T firstNonNull(T first, T second) {
        return first != null ? first : second;
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return null;
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return null;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isBlank();
    }

    private boolean isRetryable(Exception ex) {
        String message = ex.getMessage();
        if (message == null) return true;
        return message.contains("HTTP 408")
                || message.contains("HTTP 409")
                || message.contains("HTTP 429")
                || message.contains("HTTP 500")
                || message.contains("HTTP 502")
                || message.contains("HTTP 503")
                || message.contains("HTTP 504")
                || message.contains("timed out")
                || message.contains("GOAWAY");
    }

    private void pauseBeforeRetry() {
        if (retryDelayMs <= 0) return;
        try {
            Thread.sleep(retryDelayMs);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static IngredientEnrichmentResult notEnriched(String note) {
        return new IngredientEnrichmentResult(
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                List.of(),
                List.of(),
                PROVIDER,
                null,
                note,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null
        );
    }
}
