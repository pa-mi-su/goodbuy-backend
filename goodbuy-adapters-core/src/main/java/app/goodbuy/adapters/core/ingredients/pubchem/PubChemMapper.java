package app.goodbuy.adapters.core.ingredients.pubchem;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import app.goodbuy.adapters.core.ingredients.model.IngredientAlias;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Component
public class PubChemMapper {

    /**
     * Map the already-mapped PubChem JSON (from PubChemClient) into our Ingredient entity.
     *
     * Expected JSON shape (simplified):
     * {
     *   "cid": 702,
     *   "pubchemUrl": "...",
     *   "canonicalName": "Ethanol",
     *   "iupacName": "ethanol",
     *   "synonyms": [ ... ],
     *   "identity": { ... },
     *   "ghs": {
     *     "signalWord": "Danger"/"Warning"/null,
     *     "hazardStatements": [ "H225: Highly flammable liquid and vapor [...]", ... ]
     *   },
     *   "notes": {
     *     "pubchemSummary": "Ethanol is a volatile, flammable, colorless liquid..."
     *   }
     * }
     */
    public Ingredient mapToIngredient(String canonicalKey, PubChemRaw raw) {
        JsonNode root = raw.getRoot();

        Ingredient ing = new Ingredient();

        ing.setCanonicalKey(canonicalKey);
        ing.setDisplayName(extractDisplayName(root));
        ing.setCategory("chemical");

        String description = extractDescription(root);

        // ── derive hazards, concern summary + score/grade ───────────────
        List<String> hazardStatements = extractHazardStatements(root);
        String concernSummary = buildConcernSummary(hazardStatements);
        ScoreRating scoreRating = scoreFromHazards(hazardStatements);

        ing.setSummary(description);
        ing.setDescription(description);
        ing.setFuncUse(null); // TODO: real “function/use” later
        ing.setConcerns(concernSummary);

        ing.setSafetyScore(scoreRating.score());
        ing.setRatingLetter(scoreRating.letter());
        ing.setReferencesCount(scoreRating.referencesCount());
        ing.setRegulationNotes(scoreRating.note());
        ing.setActive(true);

        OffsetDateTime now = OffsetDateTime.now();
        ing.setCreatedAt(now);
        ing.setUpdatedAt(now);

        // Aliases from synonyms
        List<IngredientAlias> aliases = new ArrayList<>();
        for (String syn : extractSynonyms(root)) {
            IngredientAlias a = new IngredientAlias();
            a.setAlias(syn);
            a.setIngredient(ing);
            a.setCreatedAt(now);
            aliases.add(a);
        }
        ing.setAliases(aliases);

        // Source URL from PubChem
        List<String> sourceUrls = new ArrayList<>();
        String pubchemUrl = extractPubChemUrl(root);
        if (pubchemUrl != null) {
            sourceUrls.add(pubchemUrl);
        }
        ing.setSourceUrls(sourceUrls);

        // No tags yet – you can add tagging later from your own logic
        ing.setTags(new ArrayList<>());

        return ing;
    }

    // ──────────────────────────────────────────────────────────────
    // Extractors – using the *new* mapped JSON shape
    // ──────────────────────────────────────────────────────────────

    /**
     * Prefer a human-friendly name:
     *   1) canonicalName
     *   2) iupacName
     *   3) first synonym
     *   4) fallback "Chemical {cid}" / "Unknown Chemical"
     */
    private String extractDisplayName(JsonNode root) {
        if (root == null || root.isMissingNode()) {
            return "Unknown Chemical";
        }

        String canonicalName = textOrNull(root.get("canonicalName"));
        if (canonicalName != null && !canonicalName.isBlank()) {
            return canonicalName;
        }

        String iupacName = textOrNull(root.get("iupacName"));
        if (iupacName != null && !iupacName.isBlank()) {
            return iupacName;
        }

        List<String> synonyms = extractSynonyms(root);
        if (!synonyms.isEmpty()) {
            return synonyms.get(0);
        }

        JsonNode cidNode = root.get("cid");
        if (cidNode != null && cidNode.isInt()) {
            return "Chemical " + cidNode.asInt();
        }

        return "Unknown Chemical";
    }

    /**
     * Description: ONLY use notes.pubchemSummary / extraDescription
     * that the PubChemClient mapped from PUG-View.
     *
     * No more made-up "used in household products" text.
     */
    private String extractDescription(JsonNode root) {
        if (root == null || root.isMissingNode()) return null;

        JsonNode notes = root.get("notes");
        if (notes != null && !notes.isMissingNode()) {
            // 1) main PubChem summary we build in PubChemClient
            String fromSummary = textOrNull(notes.get("pubchemSummary"));
            if (fromSummary != null && !fromSummary.isBlank()) {
                String trimmed = fromSummary.trim();
                if (trimmed.length() > 2000) {
                    trimmed = trimmed.substring(0, 2000) + "…";
                }
                return trimmed;
            }

            // 2) optional extra description if we ever add it
            String extra = textOrNull(notes.get("extraDescription"));
            if (extra != null && !extra.isBlank()) {
                String trimmed = extra.trim();
                if (trimmed.length() > 2000) {
                    trimmed = trimmed.substring(0, 2000) + "…";
                }
                return trimmed;
            }
        }

        // If PubChemClient didn't give us any summary,
        // we deliberately return null so the UI shows no description
        // instead of fake filler text.
        return null;
    }

    /**
     * Synonyms: plain list from root.synonyms[]
     */
    private List<String> extractSynonyms(JsonNode root) {
        List<String> list = new ArrayList<>();
        if (root == null || root.isMissingNode()) return list;

        JsonNode synonyms = root.get("synonyms");
        if (synonyms != null && synonyms.isArray()) {
            for (JsonNode n : synonyms) {
                String s = textOrNull(n);
                if (s != null && !s.isBlank()) {
                    list.add(s);
                }
            }
        }
        return list;
    }

    /**
     * PubChem URL for traceability.
     */
    private String extractPubChemUrl(JsonNode root) {
        if (root == null || root.isMissingNode()) return null;
        return textOrNull(root.get("pubchemUrl"));
    }

    /**
     * Full hazard statements text from ghs.hazardStatements[].
     */
    private List<String> extractHazardStatements(JsonNode root) {
        List<String> hazards = new ArrayList<>();
        if (root == null || root.isMissingNode()) return hazards;

        JsonNode ghs = root.get("ghs");
        if (ghs == null || ghs.isMissingNode()) return hazards;

        JsonNode hazardStatements = ghs.get("hazardStatements");
        if (hazardStatements == null || !hazardStatements.isArray()) return hazards;

        for (JsonNode stmtNode : hazardStatements) {
            String raw = textOrNull(stmtNode);
            if (raw != null && !raw.isBlank()) {
                hazards.add(raw.trim());
            }
        }

        // Deduplicate but keep order
        Set<String> set = new LinkedHashSet<>(hazards);
        return new ArrayList<>(set);
    }

    // ──────────────────────────────────────────────────────────────
    // Concern summary (short, human friendly)
    // ──────────────────────────────────────────────────────────────

    /**
     * Build a short, human-friendly concerns summary from hazard statements.
     */
    private String buildConcernSummary(List<String> hazards) {
        if (hazards == null || hazards.isEmpty()) return null;

        boolean fire = false;
        boolean irritation = false;
        boolean longTerm = false;
        boolean environment = false;

        for (String h : hazards) {
            String s = h.toLowerCase();

            if (s.contains("flammable")) {
                fire = true;
            }
            if (s.contains("skin irritation") || s.contains("eye irritation")
                    || s.contains("respiratory irritation") || s.contains("sensitization")
                    || s.contains("irritation")) {
                irritation = true;
            }
            if (s.contains("cancer") || s.contains("carcinogen")
                    || s.contains("mutagen") || s.contains("mutagenic")
                    || s.contains("fertility") || s.contains("unborn")
                    || s.contains("organ damage") || s.contains("repeated exposure")
                    || s.contains("long-term")) {
                longTerm = true;
            }
            if (s.contains("aquatic life") || s.contains("environment")
                    || (s.contains("persistent") && s.contains("bioaccumulative"))) {
                environment = true;
            }
        }

        List<String> bullets = new ArrayList<>();

        if (fire) {
            bullets.add("Fire & explosion: Highly flammable liquid or vapor.");
        }
        if (irritation) {
            bullets.add("Irritation: Can irritate eyes, skin, or breathing.");
        }
        if (longTerm) {
            bullets.add("Long-term: Possible cancer, fertility or organ damage.");
        }
        if (environment) {
            bullets.add("Environment: Harmful to aquatic life or ecosystems.");
        }

        if (bullets.isEmpty()) {
            return null;
        }

        // UI splits on " • " to show bullets
        return String.join(" • ", bullets);
    }

    // ──────────────────────────────────────────────────────────────
    // Scoring – aligned with GoodBuy v1 spec
    // ──────────────────────────────────────────────────────────────

    private ScoreRating scoreFromHazards(List<String> hazards) {
        // Split into health vs environmental hazards by simple keyword scan
        List<String> healthHazards = new ArrayList<>();
        List<String> envHazards = new ArrayList<>();

        if (hazards != null) {
            for (String h : hazards) {
                String s = h.toLowerCase();
                boolean env = s.contains("aquatic life")
                        || s.contains("environment")
                        || (s.contains("persistent") && s.contains("bioaccumulative"));

                if (env) {
                    envHazards.add(h);
                } else {
                    healthHazards.add(h);
                }
            }
        }

        int refs = (hazards == null) ? 0 : hazards.size();

        int healthScore = computeHealthScore(healthHazards);
        int envScore = computeEnvScore(envHazards);
        int dataConfidence = computeDataConfidence(refs);

        int overall = (int) Math.round(
                0.7 * healthScore
                        + 0.2 * envScore
                        + 0.1 * dataConfidence
        );

        String grade = toGrade(overall);
        String riskLevel = toRiskLevel(overall);

        String note = "GoodBuy v1 score: " + overall + "/100, grade " + grade +
                ", risk " + riskLevel + ". Based on PubChem GHS hazards.";

        return new ScoreRating(
                BigDecimal.valueOf(overall),
                grade,
                refs,
                note
        );
    }

    private int computeHealthScore(List<String> hazards) {
        if (hazards == null || hazards.isEmpty()) {
            return 80; // spec default when no health hazards
        }

        int score = 100;

        for (String h : hazards) {
            String s = h.toLowerCase();

            if (containsAny(s, "fatal", "carcinogenic", "cancer", "mutagen")) {
                score -= 50;
            }
            if (s.contains("severe skin burns") || s.contains("serious eye damage")) {
                score -= 35;
            }
            if (s.contains("respiratory irritation") || s.contains("respiratory sensitization")) {
                score -= 20;
            }
            if (s.contains("skin irritation") || s.contains("eye irritation")) {
                score -= 10;
            } else if (s.contains("irritation")) {
                score -= 5;
            }
        }

        return clamp(score, 0, 100);
    }

    private int computeEnvScore(List<String> hazards) {
        if (hazards == null || hazards.isEmpty()) {
            return 80; // spec default when no env hazards
        }

        int score = 100;

        for (String h : hazards) {
            String s = h.toLowerCase();

            if (s.contains("very toxic to aquatic life")) {
                score -= 40;
            } else if (s.contains("toxic to aquatic life")) {
                score -= 20;
            } else if (s.contains("harmful to aquatic life")) {
                score -= 10;
            }

            if (s.contains("persistent") && s.contains("bioaccumulative")) {
                score -= 30;
            }
        }

        return clamp(score, 0, 100);
    }

    private int computeDataConfidence(int refs) {
        if (refs <= 0) return 30;
        if (refs <= 2) return 50;
        if (refs <= 7) return 75;
        return 90;
    }

    private String toGrade(int score) {
        if (score >= 85) return "A";
        if (score >= 70) return "B";
        if (score >= 55) return "C";
        if (score >= 40) return "D";
        return "F";
    }

    private String toRiskLevel(int score) {
        if (score >= 80) return "low";
        if (score >= 50) return "moderate";
        return "high";
    }

    private boolean containsAny(String s, String... needles) {
        for (String n : needles) {
            if (s.contains(n)) return true;
        }
        return false;
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    // ──────────────────────────────────────────────────────────────
    // Helpers
    // ──────────────────────────────────────────────────────────────

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) return null;
        String v = node.asText();
        return (v == null || v.isBlank()) ? null : v;
    }

    private record ScoreRating(BigDecimal score,
                               String letter,
                               int referencesCount,
                               String note) {}
}
