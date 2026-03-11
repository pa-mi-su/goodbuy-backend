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

    public OpenAiIngredientAutoEnricher(
            Optional<PubChemIngredientAutoEnricher> pubChem,
            @Value("${goodbuy.ingredients.auto-enrich.openai.api-url:https://api.openai.com/v1/chat/completions}") String apiUrl,
            @Value("${goodbuy.ingredients.auto-enrich.openai.api-key:}") String apiKey,
            @Value("${goodbuy.ingredients.auto-enrich.openai.model:gpt-4.1-mini}") String model,
            @Value("${goodbuy.ingredients.auto-enrich.openai.connect-timeout-ms:2000}") long connectTimeoutMs,
            @Value("${goodbuy.ingredients.auto-enrich.openai.read-timeout-ms:8000}") long readTimeoutMs
    ) {
        this.pubChem = pubChem.orElse(null);
        this.apiUrl = apiUrl.trim();
        this.apiKey = apiKey.trim();
        this.model = model.trim();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        log.info("OpenAiIngredientAutoEnricher configured model={} apiUrl={} pubChemSupport={}",
                this.model, this.apiUrl, this.pubChem != null);
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

        try {
            String responseJson = callOpenAi(req, pubChemResult);
            JsonNode payload = om.readTree(responseJson);
            IngredientEnrichmentResult merged = merge(req, parsePayload(payload), pubChemResult);
            if (!isComplete(merged)) {
                return notEnriched("openai_incomplete_profile");
            }
            return merged;
        } catch (Exception ex) {
            log.warn("OpenAiIngredientAutoEnricher failed canonicalKey='{}': {}", req.canonicalKey(), ex.toString());
            return notEnriched("openai_exception_" + ex.getClass().getSimpleName());
        }
    }

    private String callOpenAi(IngredientEnrichmentRequest req, IngredientEnrichmentResult pubChemResult) throws Exception {
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
                                        Use concise factual language.
                                        If evidence is weak, still provide the best supported low-confidence normalized value instead of leaving fields blank.
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
        JsonNode content = root.path("choices").path(0).path("message").path("content");
        if (!content.isTextual() || content.asText().isBlank()) {
            throw new IllegalStateException("OpenAI response did not contain message content");
        }
        return content.asText();
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
                firstNonNull(openAi.iarcGroup(), pubChemResult == null ? null : pubChemResult.iarcGroup()),
                firstNonNull(openAi.prop65Listed(), pubChemResult == null ? null : pubChemResult.prop65Listed()),
                firstNonNull(openAi.ewgScore(), pubChemResult == null ? null : pubChemResult.ewgScore()),
                firstNonNull(openAi.euProhibited(), pubChemResult == null ? null : pubChemResult.euProhibited()),
                firstNonNull(openAi.euRestricted(), pubChemResult == null ? null : pubChemResult.euRestricted()),
                firstNonNull(openAi.pubchemMutagen(), pubChemResult == null ? null : pubChemResult.pubchemMutagen()),
                firstNonNull(openAi.pubchemReproductiveToxin(), pubChemResult == null ? null : pubChemResult.pubchemReproductiveToxin()),
                firstNonNull(openAi.epaChronicToxicity(), pubChemResult == null ? null : pubChemResult.epaChronicToxicity()),
                firstNonNull(openAi.skinIrritant(), pubChemResult == null ? null : pubChemResult.skinIrritant())
        );
    }

    private boolean isComplete(IngredientEnrichmentResult result) {
        return result != null
                && !isBlank(result.displayName())
                && !isBlank(result.summary())
                && !isBlank(result.description())
                && !isBlank(result.functionUse())
                && !isBlank(result.concerns())
                && !isBlank(result.category())
                && !isBlank(result.regulationNotes())
                && result.referencesCount() != null;
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
