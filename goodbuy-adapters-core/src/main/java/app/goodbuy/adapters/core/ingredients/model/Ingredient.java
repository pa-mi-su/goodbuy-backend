package app.goodbuy.adapters.core.ingredients.model;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "ingredients")
public class Ingredient {

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "canonical_key", nullable = false, unique = true)
    private String canonicalKey;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    // ── Fields from your list ─────────────────────────────────────────────────
    @Column(name = "summary", columnDefinition = "TEXT")
    private String summary;                     // TEXT

    @Column(name = "safety_score", precision = 5, scale = 2)
    private BigDecimal safetyScore;            // NUMERIC(5,2)

    // Postgres doesn't enforce length on TEXT; use CHAR(1) to truly constrain to 1.
    @Column(name = "rating_letter", columnDefinition = "CHAR(1)")
    private String ratingLetter;               // TEXT(1) → CHAR(1) in Postgres

    // JPA-friendly join table for sources (instead of TEXT[])
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "ingredient_sources", joinColumns = @JoinColumn(name = "ingredient_id"))
    @Column(name = "url", nullable = false)
    private List<String> sourceUrls = new ArrayList<>();   // TEXT[] or join table → using join table

    @Column(name = "category")
    private String category;                   // TEXT

    @Column(name = "regulation_notes", columnDefinition = "TEXT")
    private String regulationNotes;            // TEXT

    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;           // BOOLEAN DEFAULT TRUE

    @Column(name = "created_at")
    private OffsetDateTime createdAt;          // TIMESTAMPTZ

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;          // TIMESTAMPTZ

    // ── Existing fields you already had (kept) ────────────────────────────────
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "func_use", columnDefinition = "TEXT")
    private String funcUse;

    @Column(name = "concerns", columnDefinition = "TEXT")
    private String concerns;

    @Column(name = "references_count")
    private Integer referencesCount;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "ingredient_tags", joinColumns = @JoinColumn(name = "ingredient_id"))
    @Column(name = "name")
    private List<String> tags = new ArrayList<>();

    @OneToMany(mappedBy = "ingredient", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<IngredientAlias> aliases = new ArrayList<>();

    // ── Timestamps ────────────────────────────────────────────────────────────
    @PrePersist
    void onCreate() {
        var now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void onUpdate() { updatedAt = OffsetDateTime.now(); }

    // ── Getters/Setters (only essentials shown; add others as needed) ─────────
    public Long getId() { return id; }
    public String getCanonicalKey() { return canonicalKey; }
    public void setCanonicalKey(String canonicalKey) { this.canonicalKey = canonicalKey; }
    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public BigDecimal getSafetyScore() { return safetyScore; }
    public void setSafetyScore(BigDecimal safetyScore) { this.safetyScore = safetyScore; }

    public String getRatingLetter() { return ratingLetter; }
    public void setRatingLetter(String ratingLetter) { this.ratingLetter = ratingLetter; }

    public List<String> getSourceUrls() { return sourceUrls; }
    public void setSourceUrls(List<String> sourceUrls) { this.sourceUrls = sourceUrls; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getRegulationNotes() { return regulationNotes; }
    public void setRegulationNotes(String regulationNotes) { this.regulationNotes = regulationNotes; }

    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { isActive = active; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(OffsetDateTime updatedAt) { this.updatedAt = updatedAt; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getFuncUse() { return funcUse; }
    public void setFuncUse(String funcUse) { this.funcUse = funcUse; }

    public String getConcerns() { return concerns; }
    public void setConcerns(String concerns) { this.concerns = concerns; }

    public Integer getReferencesCount() { return referencesCount; }
    public void setReferencesCount(Integer referencesCount) { this.referencesCount = referencesCount; }

    public List<String> getTags() { return tags; }
    public void setTags(List<String> tags) { this.tags = tags; }

    public List<IngredientAlias> getAliases() { return aliases; }
    public void setAliases(List<IngredientAlias> aliases) { this.aliases = aliases; }
}
