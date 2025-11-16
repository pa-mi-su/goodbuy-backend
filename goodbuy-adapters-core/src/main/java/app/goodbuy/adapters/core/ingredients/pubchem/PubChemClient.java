package app.goodbuy.adapters.core.ingredients.pubchem;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class PubChemClient {

    private static final Logger log = LoggerFactory.getLogger(PubChemClient.class);

    private final HttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    // PUG REST for core data (CID, properties, synonyms)
    private final String baseUrl;
    // PUG-View for GHS + human-readable summary
    private final String viewBaseUrl;

    private final int connectTimeoutMs;
    private final int readTimeoutMs;

    public PubChemClient(String baseUrl, int connectTimeoutMs, int readTimeoutMs) {
        this.baseUrl = (baseUrl == null || baseUrl.isBlank())
                ? "https://pubchem.ncbi.nlm.nih.gov/rest/pug"
                : baseUrl.replaceAll("/+$", "");
        // PUG-View has a separate root
        this.viewBaseUrl = "https://pubchem.ncbi.nlm.nih.gov/rest/pug_view/data";

        this.connectTimeoutMs = connectTimeoutMs;
        this.readTimeoutMs = readTimeoutMs;

        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(Math.max(100, connectTimeoutMs)))
                .build();

        log.info("PubChemClient initialized baseUrl={} viewBaseUrl={} connect={}ms read={}ms",
                this.baseUrl, this.viewBaseUrl, this.connectTimeoutMs, this.readTimeoutMs);
    }

    /**
     * Main entrypoint: try to fetch by ingredient name.
     *
     * Returns a PubChemRaw whose internal JSON looks like:
     *
     * {
     *   "cid": 702,
     *   "pubchemUrl": "https://pubchem.ncbi.nlm.nih.gov/compound/702",
     *   "canonicalName": "Ethanol",
     *   "iupacName": "ethanol",
     *   "synonyms": [...],
     *   "identity": {
     *     "molecularFormula": "...",
     *     "molecularWeight": 0.0,
     *     "inchiKey": "...",
     *     "smiles": "..."
     *   },
     *   "ghs": {
     *     "signalWord": "...",
     *     "hazardClasses": [...],
     *     "hazardStatements": [...],
     *     "pictograms": [...]
     *   },
     *   "notes": {
     *     "pubchemSummary": "..."
     *   }
     * }
     */
    public PubChemRaw fetch(String rawName) {
        String original = rawName == null ? "" : rawName.trim();

        log.info("PubChemClient.fetch() called with '{}'", original);

        String cleaned = cleanNameForPubChem(original);
        if (cleaned == null || cleaned.isBlank()) {
            String msg = "PubChem: cleaned name is empty for '" + original + "'";
            log.warn(msg);
            return PubChemRaw.empty(msg);
        }

        log.info("PubChemClient.fetch: original='{}' → cleaned='{}'", original, cleaned);

        List<String> variants = buildNameVariants(cleaned);
        String lastError = null;

        for (String variant : variants) {
            try {
                log.info("PubChem: attempting query-param variant '{}'", variant);

                // 1) PUG REST: name → CID + core properties
                JsonNode nameRoot = fetchByNameQueryParam(variant);
                int cid = extractCid(nameRoot);
                if (cid <= 0) {
                    lastError = "No CID found in PC_Compounds for '" + variant + "'";
                    log.warn("PubChem: {}", lastError);
                    continue;
                }

                // 2) PUG REST: synonyms
                List<String> synonyms = fetchSynonyms(cid);

                // 3) PUG-View: rich, human-readable info (GHS, summary)
                JsonNode viewRoot = fetchPugViewByCid(cid);

                // 4) Map EVERYTHING into a compact, human-usable JSON for GoodBuy
                ObjectNode mapped = mapToGoodBuyJson(original, cleaned, cid, nameRoot, viewRoot, synonyms);

                log.info("PubChem: SUCCESS for variant='{}' (cleaned='{}', cid={})", variant, cleaned, cid);
                return new PubChemRaw(mapped);

            } catch (PubChemTransportException e) {
                lastError = e.getMessage();
                log.warn("PubChem: variant '{}' failed: {}", variant, e.getMessage());
            } catch (Exception e) {
                lastError = e.toString();
                log.warn("PubChem: unexpected error for variant '{}'", variant, e);
            }
        }

        String msg = "PubChem: all name variants failed for '" + original + "'."
                + (lastError != null ? " Last error: " + lastError : "");
        return PubChemRaw.empty(msg);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Name normalization
    // ─────────────────────────────────────────────────────────────────────────

    private String cleanNameForPubChem(String raw) {
        if (raw == null) return null;

        String s = raw.trim();
        if (s.isEmpty()) return null;

        s = s.toLowerCase(Locale.ROOT);

        // Drop anything in parentheses (marketing descriptors, uses)
        s = s.replaceAll("\\([^)]*\\)", " ");

        // Remove "v/v", "w/w" and similar formulation markers
        s = s.replaceAll("\\b(v\\s*/\\s*v|w\\s*/\\s*w)\\b", " ");

        // Drop percentage expressions: "70 %", "70%", "70.0 %"
        s = s.replaceAll("\\b\\d+(\\.\\d+)?\\s*%\\b", " ");

        // Drop standalone numbers (e.g., "70", "95", "200 proof")
        s = s.replaceAll("\\b\\d+(\\.\\d+)?\\b", " ");

        // Remove stray punctuation except spaces and hyphens
        s = s.replaceAll("[^a-z0-9\\s-]", " ");

        // Collapse multiple spaces / hyphens into single spaces
        s = s.replaceAll("[-_]+", " ");
        s = s.replaceAll("\\s+", " ").trim();

        // Normalize known synonyms to canonical PubChem-friendly names
        if (s.equals("ethyl alcohol") || s.startsWith("ethyl alcohol ")) {
            s = "ethanol";
        }
        if (s.equals("denatured alcohol") || s.startsWith("denatured alcohol ")) {
            s = "ethanol";
        }
        if (s.equals("isopropyl alcohol") || s.startsWith("isopropyl alcohol ")) {
            s = "isopropyl alcohol";
        }

        return s.isBlank() ? null : s;
    }

    private List<String> buildNameVariants(String cleaned) {
        List<String> variants = new ArrayList<>();

        variants.add(cleaned);

        if (!cleaned.equals(cleaned.toUpperCase(Locale.ROOT))) {
            variants.add(cleaned.toUpperCase(Locale.ROOT));
        }

        variants.add("\"" + cleaned + "\"");
        if (!cleaned.equals(cleaned.toUpperCase(Locale.ROOT))) {
            variants.add("\"" + cleaned.toUpperCase(Locale.ROOT) + "\"");
        }

        return variants;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // HTTP calls
    // ─────────────────────────────────────────────────────────────────────────

    private JsonNode fetchByNameQueryParam(String nameVariant) {
        try {
            String encoded = URLEncoder.encode(nameVariant, StandardCharsets.UTF_8);
            String uri = baseUrl + "/compound/name/JSON?name=" + encoded;

            log.info("Calling PubChem by name (query param): {} (raw='{}')", uri, nameVariant);

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofMillis(Math.max(100, readTimeoutMs)))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = res.statusCode();
            String body = res.body();

            if (sc == 404) {
                log.warn("PubChem 404 NotFound for '{}': {}", nameVariant, body);
                throw new PubChemTransportException("PubChem 404 NotFound for '" + nameVariant + "': " + body);
            }
            if (sc < 200 || sc >= 300) {
                log.warn("PubChem HTTP {} for '{}': {}", sc, nameVariant, truncate(body, 400));
                throw new PubChemTransportException("PubChem http_" + sc + " for '" + nameVariant + "': " + truncate(body, 400));
            }

            JsonNode root = om.readTree(body);
            if (root == null || root.isEmpty()) {
                throw new PubChemTransportException("PubChem empty JSON for '" + nameVariant + "'");
            }

            return root;
        } catch (PubChemTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new PubChemTransportException("PubChem I/O error for '" + nameVariant + "': " + e.getMessage(), e);
        }
    }

    private JsonNode fetchPugViewByCid(int cid) {
        try {
            String uri = viewBaseUrl + "/compound/" + cid + "/JSON";
            log.info("Calling PubChem PUG-View by CID: {}", uri);

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofMillis(Math.max(100, readTimeoutMs)))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = res.statusCode();
            String body = res.body();

            if (sc == 404) {
                log.warn("PubChem PUG-View 404 for cid={}: {}", cid, body);
                throw new PubChemTransportException("PubChem view_404 for cid=" + cid + ": " + body);
            }
            if (sc < 200 || sc >= 300) {
                log.warn("PubChem PUG-View HTTP {} for cid={}: {}", sc, cid, truncate(body, 400));
                throw new PubChemTransportException("PubChem view_http_" + sc + " for cid=" + cid + ": " + truncate(body, 400));
            }

            JsonNode root = om.readTree(body);
            if (root == null || root.isEmpty()) {
                throw new PubChemTransportException("PubChem PUG-View empty JSON for cid=" + cid);
            }

            return root;
        } catch (PubChemTransportException e) {
            throw e;
        } catch (Exception e) {
            throw new PubChemTransportException("PubChem PUG-View I/O error for cid=" + cid + ": " + e.getMessage(), e);
        }
    }

    private List<String> fetchSynonyms(int cid) {
        List<String> result = new ArrayList<>();
        try {
            String uri = baseUrl + "/compound/cid/" + cid + "/synonyms/JSON";
            log.info("Calling PubChem synonyms for cid={}: {}", cid, uri);

            HttpRequest req = HttpRequest.newBuilder(URI.create(uri))
                    .timeout(Duration.ofMillis(Math.max(100, readTimeoutMs)))
                    .header("Accept", "application/json")
                    .GET()
                    .build();

            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            int sc = res.statusCode();
            String body = res.body();

            if (sc == 404) {
                log.warn("PubChem synonyms 404 for cid={}: {}", cid, body);
                return result;
            }
            if (sc < 200 || sc >= 300) {
                log.warn("PubChem synonyms HTTP {} for cid={}: {}", sc, cid, truncate(body, 400));
                return result;
            }

            JsonNode root = om.readTree(body);
            JsonNode synArray = root.path("InformationList")
                    .path("Information");

            if (synArray.isArray()) {
                for (JsonNode info : synArray) {
                    JsonNode syns = info.path("Synonym");
                    if (syns.isArray()) {
                        for (JsonNode s : syns) {
                            String val = s.asText(null);
                            if (val != null && !val.isBlank()) {
                                result.add(val);
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("PubChem: error fetching synonyms for cid={}: {}", cid, e.toString());
        }
        return result;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Mapping helpers
    // ─────────────────────────────────────────────────────────────────────────

    private int extractCid(JsonNode nameRoot) {
        JsonNode compounds = nameRoot.path("PC_Compounds");
        if (!compounds.isArray() || compounds.isEmpty()) return -1;
        JsonNode first = compounds.get(0);
        return first
                .path("id")
                .path("id")
                .path("cid")
                .asInt(-1);
    }

    private ObjectNode mapToGoodBuyJson(
            String original,
            String cleaned,
            int cid,
            JsonNode nameRoot,
            JsonNode viewRoot,
            List<String> synonyms
    ) {
        ObjectNode root = om.createObjectNode();

        root.put("cid", cid);
        root.put("pubchemUrl", "https://pubchem.ncbi.nlm.nih.gov/compound/" + cid);

        // Identity from PC_Compounds props
        String canonicalName = cleaned; // fallback
        String iupacName = null;
        String molecularFormula = null;
        Double molecularWeight = null;
        String inchiKey = null;
        String smiles = null;

        JsonNode compounds = nameRoot.path("PC_Compounds");
        if (compounds.isArray() && !compounds.isEmpty()) {
            JsonNode first = compounds.get(0);
            JsonNode props = first.path("props");

            molecularFormula = findProp(props, "Molecular Formula", null);
            String mwStr = findProp(props, "Molecular Weight", null);
            if (mwStr != null) {
                try {
                    molecularWeight = Double.parseDouble(mwStr);
                } catch (NumberFormatException ignored) {
                }
            }
            inchiKey = findProp(props, "InChIKey", "Standard");
            // try several SMILES variants
            smiles = findProp(props, "SMILES", "Canonical");
            if (smiles == null) smiles = findProp(props, "SMILES", "Isomeric");
            if (smiles == null) smiles = findProp(props, "SMILES", "Absolute");
        }

        // From PUG-View: title, IUPAC name, summary, GHS
        String summary = null;
        Hazards hazards = new Hazards();

        JsonNode record = viewRoot.path("Record");
        if (!record.isMissingNode()) {
            String title = record.path("RecordTitle").asText(null);
            if (title != null && !title.isBlank()) {
                canonicalName = title;
            }

            // IUPAC Name
            List<String> iupacList = new ArrayList<>();
            collectStringsForHeading(record, "IUPAC Name", iupacList);
            if (!iupacList.isEmpty()) {
                iupacName = iupacList.get(0);
            }

            // 🔎 Summary: be generous – collect any section whose TOCHeading
            // contains "description" (case-insensitive). This is where the
            // sodium carbonate multi-source block lives.
            List<String> summaries = new ArrayList<>();
            collectStringsForHeadingContains(record, "description", summaries);
            if (!summaries.isEmpty()) {
                int maxPara = Math.min(5, summaries.size());
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < maxPara; i++) {
                    if (i > 0) {
                        sb.append("\n\n");
                    }
                    sb.append(summaries.get(i).trim());
                }
                summary = sb.toString();
            }

            // GHS
            List<String> ghsStrings = new ArrayList<>();
            collectStringsForHeading(record, "GHS Classification", ghsStrings);
            hazards = extractHazardsFromStrings(ghsStrings);
        }

        root.put("canonicalName", canonicalName);
        root.put("iupacName", iupacName);

        ArrayNode synArray = root.putArray("synonyms");
        int maxSyn = 30;
        int count = 0;
        for (String s : synonyms) {
            if (count >= maxSyn) break;
            synArray.add(s);
            count++;
        }

        ObjectNode identity = root.putObject("identity");
        identity.put("molecularFormula", molecularFormula);
        if (molecularWeight != null) {
            identity.put("molecularWeight", molecularWeight);
        } else {
            identity.putNull("molecularWeight");
        }
        identity.put("inchiKey", inchiKey);
        identity.put("smiles", smiles);

        ObjectNode ghs = root.putObject("ghs");
        ghs.put("signalWord", hazards.signalWord);

        ArrayNode hazardClasses = ghs.putArray("hazardClasses");
        for (String hc : hazards.hazardClasses) {
            hazardClasses.add(hc);
        }

        ArrayNode hazardStatements = ghs.putArray("hazardStatements");
        for (String hs : hazards.hazardStatements) {
            hazardStatements.add(hs);
        }

        ArrayNode pictograms = ghs.putArray("pictograms");
        for (String pg : hazards.pictograms) {
            pictograms.add(pg);
        }

        ObjectNode notes = root.putObject("notes");
        notes.put("pubchemSummary", summary);

        // Debug log of the final mapped JSON
        log.info("PubChem mapped JSON for '{}': {}", original, root.toPrettyString());

        return root;
    }

    private String findProp(JsonNode props, String label, String name) {
        if (!props.isArray()) return null;

        for (JsonNode prop : props) {
            JsonNode urn = prop.path("urn");
            String lbl = urn.path("label").asText(null);
            String nm = urn.path("name").asText(null);

            if (label != null && (lbl == null || !lbl.equals(label))) {
                continue;
            }
            if (name != null && (nm == null || !nm.equals(name))) {
                continue;
            }

            JsonNode value = prop.path("value");
            if (value.has("sval")) return value.path("sval").asText(null);
            if (value.has("fval")) return String.valueOf(value.path("fval").asDouble());
            if (value.has("ival")) return String.valueOf(value.path("ival").asInt());
        }
        return null;
    }

    /**
     * Recursively collect all string values from "Information" nodes
     * under any section whose TOCHeading == heading.
     */
    private void collectStringsForHeading(JsonNode node, String heading, List<String> out) {
        if (node == null || node.isMissingNode()) return;

        if (heading.equals(node.path("TOCHeading").asText())) {
            JsonNode infos = node.path("Information");
            if (infos.isArray()) {
                for (JsonNode info : infos) {
                    JsonNode value = info.path("Value");
                    if (value.isMissingNode()) continue;

                    JsonNode swm = value.path("StringWithMarkup");
                    if (swm.isArray()) {
                        for (JsonNode s : swm) {
                            String txt = s.path("String").asText(null);
                            if (txt != null && !txt.isBlank()) {
                                out.add(txt.trim());
                            }
                        }
                    } else {
                        String txt = value.path("String").asText(null);
                        if (txt != null && !txt.isBlank()) {
                            out.add(txt.trim());
                        }
                    }
                }
            }
        }

        // Recurse into child sections
        JsonNode sections = node.path("Section");
        if (sections.isArray()) {
            for (JsonNode sec : sections) {
                collectStringsForHeading(sec, heading, out);
            }
        }
    }

    /**
     * Like collectStringsForHeading, but matches TOCHeading if it *contains*
     * a fragment (case-insensitive). This catches "Record Description",
     * "Description", etc.
     */
    private void collectStringsForHeadingContains(JsonNode node, String headingFragment, List<String> out) {
        if (node == null || node.isMissingNode()) return;

        String toc = node.path("TOCHeading").asText("");
        if (!toc.isEmpty()) {
            String tocLower = toc.toLowerCase(Locale.ROOT);
            String fragLower = headingFragment.toLowerCase(Locale.ROOT);

            if (tocLower.contains(fragLower)) {
                JsonNode infos = node.path("Information");
                if (infos.isArray()) {
                    for (JsonNode info : infos) {
                        JsonNode value = info.path("Value");
                        if (value.isMissingNode()) continue;

                        JsonNode swm = value.path("StringWithMarkup");
                        if (swm.isArray()) {
                            for (JsonNode s : swm) {
                                String txt = s.path("String").asText(null);
                                if (txt != null && !txt.isBlank()) {
                                    out.add(txt.trim());
                                }
                            }
                        } else {
                            String txt = value.path("String").asText(null);
                            if (txt != null && !txt.isBlank()) {
                                out.add(txt.trim());
                            }
                        }
                    }
                }
            }
        }

        // Recurse into child sections
        JsonNode sections = node.path("Section");
        if (sections.isArray()) {
            for (JsonNode sec : sections) {
                collectStringsForHeadingContains(sec, headingFragment, out);
            }
        }
    }

    private Hazards extractHazardsFromStrings(List<String> lines) {
        Hazards hz = new Hazards();

        for (String line : lines) {
            String lower = line.toLowerCase(Locale.ROOT);

            // Signal word (Danger / Warning)
            if (hz.signalWord == null && lower.contains("signal word")) {
                // e.g. "Signal word: Danger"
                int idx = line.indexOf(':');
                if (idx >= 0 && idx + 1 < line.length()) {
                    hz.signalWord = line.substring(idx + 1).trim();
                }
            }

            // Hazard statements: Hxxx...
            if (line.matches("(?i)^H\\d{3}.*")) {
                hz.hazardStatements.add(line.trim());
            }

            // Hazard classes: often contain "Category"
            if (lower.contains("category")) {
                hz.hazardClasses.add(line.trim());
            }

            // Pictograms codes like GHS02, GHS07, etc.
            if (lower.contains("ghs")) {
                // Very simple extraction: split on non-alphanum, look for GHSxx
                for (String part : line.split("[^A-Za-z0-9]+")) {
                    if (part.matches("GHS\\d{2}")) {
                        hz.pictograms.add(part);
                    }
                }
            }
        }

        return hz;
    }

    private static class Hazards {
        String signalWord;
        List<String> hazardClasses = new ArrayList<>();
        List<String> hazardStatements = new ArrayList<>();
        List<String> pictograms = new ArrayList<>();
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // Simple wrapper exception for adapter-level issues
    private static class PubChemTransportException extends RuntimeException {
        PubChemTransportException(String msg) { super(msg); }
        PubChemTransportException(String msg, Throwable cause) { super(msg, cause); }
    }
}
