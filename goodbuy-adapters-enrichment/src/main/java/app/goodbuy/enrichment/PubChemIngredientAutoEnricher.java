package app.goodbuy.enrichment;

import app.goodbuy.core.ingredients.port.IngredientAutoEnricherPort;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentRequest;
import app.goodbuy.core.ingredients.port.IngredientEnrichmentResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * PubChem-backed ingredient enrichment (best-effort).
 *
 * IMPORTANT:
 * - Adapters frequently parse note tokens. Keep them STRICT: key=value;key=value...
 * - DO NOT include quotes or freeform punctuation that can break parsing.
 *
 * NOTE TOKENS (strict, parser-safe):
 *   pubchem_cid=<CID>;pubchem_mutagen=<true|false>;pubchem_reproductive_toxin=<true|false>;
 *   pubchem_query=<sanitized>;pubchem_has_desc=<true|false>;pubchem_tier=<tier>
 */
@Component
public class PubChemIngredientAutoEnricher implements IngredientAutoEnricherPort {

    private static final Logger log = LoggerFactory.getLogger(PubChemIngredientAutoEnricher.class);

    private static final String PROVIDER = "PUBCHEM";
    private static final String PUBCHEM_WEB = "https://pubchem.ncbi.nlm.nih.gov";
    private static final int MAX_QUERY_LEN = 200;

    // Retry policy (PubChem can return 503/429 under load)
    private static final int MAX_ATTEMPTS = 4;
    private static final long BASE_BACKOFF_MS = 140;

    // Common "not-a-chemical" tokens we should not attempt to resolve as compounds.
    private static final Set<String> NON_CHEMICAL_TOKENS = Set.of(
            "softgel", "capsule", "tablet", "gelcap", "cap", "bottle", "packaging",
            "flavor", "flavour", "natural flavor", "natural flavours", "artificial flavor", "artificial flavours",
            "color", "colour", "fragrance", "parfum"
    );

    // Simple domain-ish mappings: label terms -> chemical name PubChem will understand
    private static final Map<String, String> QUERY_ALIASES = Map.ofEntries(
            Map.entry("vitamin d3", "cholecalciferol"),
            Map.entry("vitamin d-3", "cholecalciferol"),
            Map.entry("d3", "cholecalciferol"),
            Map.entry("vitamin d2", "ergocalciferol"),
            Map.entry("vitamin e", "alpha tocopherol"),
            Map.entry("d-alpha tocopherol", "alpha tocopherol"),
            Map.entry("d alpha tocopherol", "alpha tocopherol"),
            Map.entry("folic acid", "pteroylglutamic acid"),
            Map.entry("vitamin b9", "pteroylglutamic acid"),

            // BIG ONE: polymer naming on labels varies wildly
            Map.entry("hydroxypropyl methylcellulose", "hydroxypropylmethylcellulose"),
            Map.entry("hydroxypropylmethylcellulose", "hydroxypropylmethylcellulose"),
            Map.entry("hypromellose", "hydroxypropylmethylcellulose"),
            Map.entry("hpmc", "hydroxypropylmethylcellulose"),
            Map.entry("e464", "hydroxypropylmethylcellulose")
    );

    private final ObjectMapper om = new ObjectMapper();
    private final HttpClient httpClient;
    private final String baseUrl; // e.g. https://pubchem.ncbi.nlm.nih.gov/rest/pug
    private final long readTimeoutMs;

    public PubChemIngredientAutoEnricher(
            @Value("${goodbuy.ingredients.auto-enrich.pubchem.base-url:https://pubchem.ncbi.nlm.nih.gov/rest/pug}") String baseUrl,
            @Value("${goodbuy.ingredients.auto-enrich.pubchem.connect-timeout-ms:800}") long connectTimeoutMs,
            @Value("${goodbuy.ingredients.auto-enrich.pubchem.read-timeout-ms:1600}") long readTimeoutMs
    ) {
        this.baseUrl = trimTrailingSlash(baseUrl);
        this.readTimeoutMs = readTimeoutMs;

        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();

        log.info("PubChemIngredientAutoEnricher configured baseUrl={} connectTimeoutMs={} readTimeoutMs={}",
                this.baseUrl, connectTimeoutMs, readTimeoutMs);
    }

    @Override
    public IngredientEnrichmentResult enrich(IngredientEnrichmentRequest req) {
        if (req == null) return notEnriched("request_null");

        String canonicalKey = safe(req.canonicalKey()).trim();
        String displayName = safe(req.displayName()).trim();

        if (canonicalKey.isBlank()) return notEnriched("canonicalKey_blank");

        // Choose query: prefer displayName, else canonicalKey
        String query = !displayName.isBlank() ? displayName : canonicalKey;
        query = normalizeQuery(query);

        // Guard: obvious non-chemical tokens
        String qLower = query.toLowerCase(Locale.ROOT).trim();
        if (NON_CHEMICAL_TOKENS.contains(qLower)) {
            return notEnriched("skipped_non_chemical_token");
        }

        // Apply simple alias mapping
        query = applyAliasMapping(query);

        // Keep a sanitized version for note tokens (parser-safe)
        String queryForTokens = sanitizeTokenValue(query);

        try {
            LookupCidResult cidRes = lookupCidByName(query);

            if (cidRes == null || cidRes.cid() == null) {
                log.info("PubChem enrich NO_CID canonicalKey='{}' query='{}'", canonicalKey, queryForTokens);
                return notEnriched("no_cid");
            }

            long cid = cidRes.cid();
            String tier = cidRes.tier();

            String compoundPage = PUBCHEM_WEB + "/compound/" + cid;

            // 1) Try PubChem description
            String summary = fetchDescriptionBestEffort(cid);
            if (summary != null) {
                summary = summary.trim();
                if (summary.isBlank()) summary = null;
            }

            // 2) Always fetch core properties (reliable) and use them as fallback summary
            PubChemProps props = fetchPropsBestEffort(cid);

            boolean hasDesc = (summary != null && !summary.isBlank());
            if (!hasDesc) {
                summary = buildFallbackSummaryFromProps(props, cid);
            }

            // ✅ ALWAYS provide category when CID is found (UI needs it)
            String category = "compound";

            List<String> urls = new ArrayList<>(4);
            urls.add(compoundPage);
            urls.add(buildUrl("/compound/cid/" + cid + "/description/JSON"));
            urls.add(buildPugViewUrl(cid));

            urls.removeIf(u -> u == null || u.isBlank());
            if (urls.isEmpty()) {
                return notEnriched("no_source_urls");
            }

            String citationTitle = "PubChem Compound Summary (CID " + cid + ")";

            // Prefer PubChem Title if available (it’s what users expect to see)
            String finalDisplayName = safe(req.displayName()).trim();
            if (props != null && !isBlank(props.title())) {
                finalDisplayName = props.title().trim();
            }
            if (finalDisplayName.isBlank()) finalDisplayName = canonicalKey;

            PubChemSignals sig = probeSignalsBestEffort(cid);

            String noteTokens = buildNoteTokens(cid, sig, queryForTokens, hasDesc, tier);

            IngredientEnrichmentResult out = enriched(
                    finalDisplayName,
                    summary,
                    category,
                    urls,
                    citationTitle,
                    noteTokens,
                    sig
            );

            log.info("PubChem enrich SUCCESS canonicalKey='{}' query='{}' cid={} tier={} hasDesc={} mutagen={} repro={}",
                    canonicalKey, queryForTokens, cid, sanitizeTokenValue(tier), hasDesc,
                    sig.pubchemMutagen(), sig.pubchemReproductiveToxin());

            return out;

        } catch (Exception ex) {
            log.warn("PubChemIngredientAutoEnricher failed canonicalKey='{}' query='{}': {}",
                    canonicalKey, queryForTokens, ex.toString());
            return notEnriched("exception_" + ex.getClass().getSimpleName());
        }
    }

    private static String buildFallbackSummaryFromProps(PubChemProps props, long cid) {
        // This is NOT a hack: it's stable PubChem data (properties endpoint)
        // and guarantees user-visible content when CID is resolved.
        if (props == null) {
            return "PubChem compound (CID " + cid + ").";
        }

        String title = safe(props.title()).trim();
        String formula = safe(props.formula()).trim();
        String mw = safe(props.molecularWeight()).trim();

        List<String> parts = new ArrayList<>();
        if (!title.isBlank()) parts.add(title);
        if (!formula.isBlank()) parts.add("Formula: " + formula);
        if (!mw.isBlank()) parts.add("Molecular weight: " + mw + " g/mol");

        if (parts.isEmpty()) {
            return "PubChem compound (CID " + cid + ").";
        }
        return "PubChem compound. " + String.join(". ", parts) + ".";
    }

    private static String buildNoteTokens(Long cid, PubChemSignals sig, String queryForTokens, boolean hasDesc, String tier) {
        return "pubchem_cid=" + cid
                + ";pubchem_mutagen=" + (sig != null && sig.pubchemMutagen())
                + ";pubchem_reproductive_toxin=" + (sig != null && sig.pubchemReproductiveToxin())
                + ";pubchem_query=" + queryForTokens
                + ";pubchem_has_desc=" + hasDesc
                + ";pubchem_tier=" + sanitizeTokenValue(safe(tier));
    }

    // ─────────────────────────────────────────────────────────────
    // CID lookup: make this HARD to fail
    // ─────────────────────────────────────────────────────────────

    private LookupCidResult lookupCidByName(String name) {
        String q = (name == null) ? "" : name.trim();
        if (q.isBlank()) return null;

        List<String> candidates = buildCandidateQueries(q);

        // Tier 1: exact-ish
        for (String cand : candidates) {
            LookupCidResult r = tryNameToCid(cand, "tier1");
            if (r != null) return r;
        }

        // Tier 2: cleaned
        for (String cand : candidates) {
            String cleaned = cleanQuery(cand);
            if (!cleaned.isBlank() && !cleaned.equalsIgnoreCase(cand)) {
                LookupCidResult r = tryNameToCid(cleaned, "tier2");
                if (r != null) return r;
            }
        }

        // Tier 3: name_type=word (more forgiving)
        for (String cand : candidates) {
            String cleaned = cleanQuery(cand);
            if (cleaned.isBlank()) continue;
            LookupCidResult r = tryNameToCidWithParams(cleaned, "tier3_word", Map.of("name_type", "word"));
            if (r != null) return r;
        }

        // Tier 4: synonyms -> resolve synonym to CID (exact and word)
        for (String cand : candidates) {
            List<String> syns = trySynonyms(cand);
            for (int i = 0; i < Math.min(12, syns.size()); i++) {
                String ss = safe(syns.get(i)).trim();
                if (ss.isBlank()) continue;

                LookupCidResult r1 = tryNameToCid(ss, "tier4_syn_exact");
                if (r1 != null) return r1;

                LookupCidResult r2 = tryNameToCidWithParams(ss, "tier4_syn_word", Map.of("name_type", "word"));
                if (r2 != null) return r2;
            }
        }

        // Tier 5: SUBSTANCE name -> CID (some label-ish names show up here)
        for (String cand : candidates) {
            String cleaned = cleanQuery(cand);
            if (cleaned.isBlank()) continue;

            LookupCidResult r = trySubstanceNameToCid(cleaned, "tier5_substance_flat");
            if (r != null) return r;
        }

        return null;
    }

    private static List<String> buildCandidateQueries(String q) {
        String base = safe(q).trim();
        if (base.isBlank()) return List.of();

        List<String> out = new ArrayList<>();
        out.add(base);

        String firstComma = base.split(",", 2)[0].trim();
        if (!firstComma.isBlank() && !firstComma.equalsIgnoreCase(base)) out.add(firstComma);

        String firstDash = base.split("\\s-\\s", 2)[0].trim();
        if (!firstDash.isBlank() && !firstDash.equalsIgnoreCase(base)) out.add(firstDash);

        String noParen = base.replaceAll("\\s*\\([^)]*\\)\\s*", " ").replaceAll("\\s+", " ").trim();
        if (!noParen.isBlank() && !noParen.equalsIgnoreCase(base)) out.add(noParen);

        LinkedHashSet<String> dedup = new LinkedHashSet<>();
        for (String s : out) {
            String x = normalizeQuery(s);
            if (!x.isBlank()) dedup.add(x);
        }
        return new ArrayList<>(dedup);
    }

    private LookupCidResult tryNameToCid(String query, String tierTag) {
        return tryNameToCidWithParams(query, tierTag, null);
    }

    private LookupCidResult tryNameToCidWithParams(String query, String tierTag, Map<String, String> params) {
        String q = safe(query).trim();
        if (q.isBlank()) return null;

        try {
            // CRITICAL FIX:
            // URLEncoder is query-string encoding (spaces -> '+'), but PubChem expects proper
            // URL path encoding (spaces -> '%20') inside /compound/name/<HERE>/...
            String encPath = encodePathSegment(q);

            String url = buildUrl("/compound/name/" + encPath + "/cids/JSON");
            url = appendQueryParams(url, params);

            HttpResponse<String> resp = sendWithRetry(url, tierTag, q);
            if (resp == null) return null;

            int status = resp.statusCode();
            if (status == 404) {
                log.info("PubChem CID lookup {} 404 query='{}'", tierTag, sanitizeTokenValue(q));
                return null;
            }
            if (status < 200 || status >= 300) {
                log.info("PubChem CID lookup {} non-2xx status={} query='{}'", tierTag, status, sanitizeTokenValue(q));
                return null;
            }

            String body = resp.body();
            JsonNode root = om.readTree(body);

            JsonNode cidArr = root.path("IdentifierList").path("CID");
            if (cidArr.isArray() && cidArr.size() > 0) {
                long cid = cidArr.get(0).asLong(0);
                if (cid > 0) {
                    log.info("PubChem CID lookup HIT {} query='{}' cid={}", tierTag, sanitizeTokenValue(q), cid);
                    return new LookupCidResult(cid, tierTag);
                }
            }

            String snippet = (body == null) ? "(null)" : body.replaceAll("\\s+", " ");
            if (snippet.length() > 250) snippet = snippet.substring(0, 250);
            log.warn("PubChem CID lookup {} 2xx but NO CID parsed. query='{}' body~='{}'",
                    tierTag, sanitizeTokenValue(q), snippet);

            return null;

        } catch (Exception ex) {
            log.info("PubChem CID lookup {} failed query='{}': {}", tierTag, sanitizeTokenValue(q), ex.toString());
            return null;
        }
    }

    private LookupCidResult trySubstanceNameToCid(String query, String tierTag) {
        String q = safe(query).trim();
        if (q.isBlank()) return null;

        try {
            String encPath = encodePathSegment(q);

            String url = buildUrl("/substance/name/" + encPath + "/cids/JSON");
            url = appendQueryParams(url, Map.of("list_return", "flat"));

            HttpResponse<String> resp = sendWithRetry(url, tierTag, q);
            if (resp == null) return null;

            int status = resp.statusCode();
            if (status == 404) {
                log.info("PubChem SUBSTANCE->CID {} 404 query='{}'", tierTag, sanitizeTokenValue(q));
                return null;
            }
            if (status < 200 || status >= 300) {
                log.info("PubChem SUBSTANCE->CID {} non-2xx status={} query='{}'", tierTag, status, sanitizeTokenValue(q));
                return null;
            }

            JsonNode root = om.readTree(resp.body());
            Long cid = firstCidFromAnyShape(root);

            if (cid != null && cid > 0) {
                log.info("PubChem SUBSTANCE->CID HIT {} query='{}' cid={}", tierTag, sanitizeTokenValue(q), cid);
                return new LookupCidResult(cid, tierTag);
            }

            String body = resp.body();
            String snippet = (body == null) ? "(null)" : body.replaceAll("\\s+", " ");
            if (snippet.length() > 250) snippet = snippet.substring(0, 250);
            log.warn("PubChem SUBSTANCE->CID {} 2xx but NO CID parsed. query='{}' body~='{}'",
                    tierTag, sanitizeTokenValue(q), snippet);

            return null;

        } catch (Exception ex) {
            log.info("PubChem SUBSTANCE->CID {} failed query='{}': {}", tierTag, sanitizeTokenValue(q), ex.toString());
            return null;
        }
    }

    private static Long firstCidFromAnyShape(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) return null;

        JsonNode a = root.path("IdentifierList").path("CID");
        Long cid = firstLongFromArray(a);
        if (cid != null) return cid;

        JsonNode b = root.path("InformationList").path("Information");
        if (b.isArray() && b.size() > 0) {
            JsonNode cidArr = b.get(0).path("CID");
            cid = firstLongFromArray(cidArr);
            if (cid != null) return cid;
        }

        JsonNode infoList = root.path("InformationList");
        if (infoList.isObject()) {
            Iterator<String> fn = infoList.fieldNames();
            while (fn.hasNext()) {
                String f = fn.next();
                JsonNode v = infoList.get(f);
                if (v == null) continue;
                if (v.isArray() && v.size() > 0) {
                    JsonNode first = v.get(0);
                    if (first != null && first.isObject() && first.has("CID")) {
                        cid = firstLongFromArray(first.get("CID"));
                        if (cid != null) return cid;
                    }
                }
            }
        }

        return null;
    }

    private static Long firstLongFromArray(JsonNode arr) {
        if (arr == null) return null;
        if (!arr.isArray() || arr.size() == 0) return null;
        long v = arr.get(0).asLong(0);
        return v > 0 ? v : null;
    }

    private HttpResponse<String> sendWithRetry(String url, String tierTag, String query) {
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(readTimeoutMs))
                        .header("Accept", "application/json")
                        .header("User-Agent", "GoodBuy/ingredient-enrichment (PubChem PUG REST)")
                        .GET()
                        .build();

                HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
                int status = resp.statusCode();

                if (status == 429 || status == 503 || status == 502 || status == 504) {
                    if (attempt < MAX_ATTEMPTS) {
                        long sleep = backoffMs(attempt);
                        log.info("PubChem call {} transient status={} attempt={}/{} query='{}' sleepMs={}",
                                tierTag, status, attempt, MAX_ATTEMPTS, sanitizeTokenValue(query), sleep);
                        Thread.sleep(sleep);
                        continue;
                    }
                }

                return resp;

            } catch (Exception ex) {
                if (attempt < MAX_ATTEMPTS) {
                    long sleep = backoffMs(attempt);
                    log.info("PubChem call {} attempt={}/{} failed query='{}': {} sleepMs={}",
                            tierTag, attempt, MAX_ATTEMPTS, sanitizeTokenValue(query), ex.toString(), sleep);
                    try { Thread.sleep(sleep); } catch (InterruptedException ignored) {}
                    continue;
                }
                return null;
            }
        }
        return null;
    }

    private static long backoffMs(int attempt) {
        long jitter = ThreadLocalRandom.current().nextLong(0, 110);
        return BASE_BACKOFF_MS * (long) attempt * (long) attempt + jitter;
    }

    private List<String> trySynonyms(String query) {
        try {
            String q = safe(query).trim();
            if (q.isBlank()) return List.of();

            String encPath = encodePathSegment(q);
            String url = buildUrl("/compound/name/" + encPath + "/synonyms/JSON");

            HttpResponse<String> resp = sendWithRetry(url, "synonyms", q);
            if (resp == null) return List.of();

            int status = resp.statusCode();
            if (status == 404) return List.of();
            if (status < 200 || status >= 300) return List.of();

            JsonNode root = om.readTree(resp.body());

            JsonNode info = root.path("InformationList").path("Information");
            if (!info.isArray() || info.size() == 0) return List.of();

            JsonNode synArr = info.get(0).path("Synonym");
            if (!synArr.isArray() || synArr.size() == 0) return List.of();

            List<String> out = new ArrayList<>();
            for (JsonNode n : synArr) {
                String s = n.asText(null);
                if (s != null && !s.isBlank()) out.add(s);
            }

            out.sort(Comparator.comparingInt(String::length));
            return out;

        } catch (Exception ex) {
            return List.of();
        }
    }

    private String fetchDescriptionBestEffort(long cid) {
        try {
            String url = buildUrl("/compound/cid/" + cid + "/description/JSON");

            HttpResponse<String> resp = sendWithRetry(url, "description", "cid:" + cid);
            if (resp == null) return null;

            int status = resp.statusCode();
            if (status < 200 || status >= 300) return null;

            JsonNode root = om.readTree(resp.body());

            JsonNode info = root.path("InformationList").path("Information");
            if (info.isArray() && info.size() > 0) {
                JsonNode first = info.get(0);
                String desc = first.path("Description").asText(null);
                if (desc != null) {
                    desc = desc.trim();
                    if (!desc.isBlank()) return desc;
                }
            }

            return null;

        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * ✅ FIX #2: Properties endpoint is reliable even when Description is missing.
     */
    private PubChemProps fetchPropsBestEffort(long cid) {
        try {
            String url = buildUrl("/compound/cid/" + cid + "/property/Title,MolecularFormula,MolecularWeight/JSON");

            HttpResponse<String> resp = sendWithRetry(url, "properties", "cid:" + cid);
            if (resp == null) return null;

            int status = resp.statusCode();
            if (status < 200 || status >= 300) return null;

            JsonNode root = om.readTree(resp.body());
            JsonNode propsArr = root.path("PropertyTable").path("Properties");
            if (!propsArr.isArray() || propsArr.size() == 0) return null;

            JsonNode first = propsArr.get(0);

            String title = first.path("Title").asText(null);
            String formula = first.path("MolecularFormula").asText(null);

            // PubChem sometimes returns numeric MW; read as text safely
            String mw = null;
            JsonNode mwNode = first.get("MolecularWeight");
            if (mwNode != null && !mwNode.isNull()) {
                mw = mwNode.asText(null);
            }

            if (isBlank(title) && isBlank(formula) && isBlank(mw)) return null;
            return new PubChemProps(title, formula, mw);

        } catch (Exception ex) {
            return null;
        }
    }

    /**
     * Best-effort heuristic to infer hazard-ish signals from PUG_VIEW JSON.
     */
    public PubChemSignals probeSignalsBestEffort(long cid) {
        try {
            String url = buildPugViewUrl(cid);

            HttpResponse<String> resp = sendWithRetry(url, "pug_view", "cid:" + cid);
            if (resp == null) return PubChemSignals.none();

            int status = resp.statusCode();
            if (status < 200 || status >= 300) return PubChemSignals.none();

            JsonNode root = om.readTree(resp.body());

            String all = root.toString().toLowerCase(Locale.ROOT);
            boolean mutagen = containsPositiveSignal(all, "mutagen");
            boolean repro = containsPositiveSignal(all, "reproductive") || containsPositiveSignal(all, "developmental");

            return new PubChemSignals(mutagen, repro);

        } catch (Exception ex) {
            return PubChemSignals.none();
        }
    }

    private static boolean containsPositiveSignal(String hay, String needle) {
        int idx = hay.indexOf(needle);
        if (idx < 0) return false;

        int start = Math.max(0, idx - 250);
        int end = Math.min(hay.length(), idx + 250);
        String window = hay.substring(start, end);

        return window.contains("positive")
                || window.contains("mutagenic")
                || window.contains("toxic")
                || window.contains("toxicity")
                || window.contains("hazard")
                || window.contains("carcin")
                || window.contains("category 1")
                || window.contains("category 2");
    }

    private String buildUrl(String path) {
        String p = safe(path);
        if (!p.startsWith("/")) p = "/" + p;
        return baseUrl + p;
    }

    private String buildPugViewUrl(long cid) {
        return PUBCHEM_WEB + "/rest/pug_view/data/compound/" + cid + "/JSON";
    }

    private static String appendQueryParams(String url, Map<String, String> params) {
        if (url == null) return "";
        if (params == null || params.isEmpty()) return url;

        StringBuilder sb = new StringBuilder(url);
        sb.append(url.contains("?") ? "&" : "?");

        boolean first = true;
        for (Map.Entry<String, String> e : params.entrySet()) {
            String k = safe(e.getKey()).trim();
            String v = safe(e.getValue()).trim();
            if (k.isBlank() || v.isBlank()) continue;

            if (!first) sb.append("&");
            first = false;

            sb.append(URLEncoder.encode(k, StandardCharsets.UTF_8));
            sb.append("=");
            sb.append(URLEncoder.encode(v, StandardCharsets.UTF_8));
        }

        String out = sb.toString();
        if (out.endsWith("?") || out.endsWith("&")) {
            return url;
        }
        return out;
    }

    private static String trimTrailingSlash(String s) {
        if (s == null) return "";
        String x = s.trim();
        while (x.endsWith("/")) x = x.substring(0, x.length() - 1);
        return x;
    }

    private static String safe(String s) {
        return s == null ? "" : s;
    }

    private static String normalizeQuery(String q) {
        String x = safe(q).trim();
        if (x.length() > MAX_QUERY_LEN) x = x.substring(0, MAX_QUERY_LEN);
        return x;
    }

    private static String cleanQuery(String q) {
        String cleaned = safe(q)
                .replaceAll("\\s*\\([^)]*\\)\\s*", " ")
                .replaceAll("[^\\p{L}\\p{N}\\s\\-]", " ")
                .replaceAll("\\s+", " ")
                .trim();

        if (cleaned.length() > MAX_QUERY_LEN) cleaned = cleaned.substring(0, MAX_QUERY_LEN);
        return cleaned;
    }

    private static String applyAliasMapping(String query) {
        String q = safe(query).trim();
        String lower = q.toLowerCase(Locale.ROOT);

        String mapped = QUERY_ALIASES.get(lower);
        if (mapped != null) return mapped;

        for (Map.Entry<String, String> e : QUERY_ALIASES.entrySet()) {
            if (lower.contains(e.getKey())) return e.getValue();
        }
        return q;
    }

    /**
     * Make values safe for "key=value;key=value" parsing:
     * - replace ';' and '=' with '_'
     * - collapse whitespace
     * - trim
     */
    private static String sanitizeTokenValue(String v) {
        String x = safe(v).trim();
        x = x.replace(';', '_').replace('=', '_');
        x = x.replaceAll("\\s+", " ");
        if (x.length() > MAX_QUERY_LEN) x = x.substring(0, x.length() - 0); // keep as-is; already capped
        if (x.length() > MAX_QUERY_LEN) x = x.substring(0, MAX_QUERY_LEN);
        return x;
    }

    /**
     * Encode a value intended to live in a URL PATH segment.
     *
     * DO NOT use URLEncoder output directly for path segments because it turns spaces into '+',
     * which is NOT a space in a URL path.
     */
    private static String encodePathSegment(String raw) {
        String x = safe(raw).trim();
        if (x.isBlank()) return "";
        String enc = URLEncoder.encode(x, StandardCharsets.UTF_8);
        return enc.replace("+", "%20");
    }

    // ─────────────────────────────────────────────────────────────
    // Result builders
    // ─────────────────────────────────────────────────────────────

    private static IngredientEnrichmentResult notEnriched(String note) {
        return new IngredientEnrichmentResult(
                false,
                null,     // displayName
                null,     // summary
                null,     // category
                null,     // sourceUrls
                PROVIDER, // provider
                null,     // citationTitle
                note,     // note

                null,     // iarcGroup
                null,     // prop65Listed
                null,     // ewgScore
                null,     // euProhibited
                null,     // euRestricted
                null,     // pubchemMutagen
                null,     // pubchemReproductiveToxin
                null,     // epaChronicToxicity
                null      // skinIrritant
        );
    }

    private static IngredientEnrichmentResult enriched(
            String displayName,
            String summary,
            String category,
            List<String> sourceUrls,
            String citationTitle,
            String note,
            PubChemSignals sig
    ) {
        return new IngredientEnrichmentResult(
                true,
                displayName,
                summary,
                category,
                sourceUrls,
                PROVIDER,
                citationTitle,
                note,

                null,                           // iarcGroup
                null,                           // prop65Listed
                null,                           // ewgScore
                null,                           // euProhibited
                null,                           // euRestricted
                sig != null ? sig.pubchemMutagen() : null,
                sig != null ? sig.pubchemReproductiveToxin() : null,
                null,                           // epaChronicToxicity
                null                            // skinIrritant
        );
    }

    public record PubChemSignals(boolean pubchemMutagen, boolean pubchemReproductiveToxin) {
        public static PubChemSignals none() { return new PubChemSignals(false, false); }
    }

    private record LookupCidResult(Long cid, String tier) { }

    private record PubChemProps(String title, String formula, String molecularWeight) { }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isBlank();
    }
}
