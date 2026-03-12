package app.goodbuy.adapters.core.products.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * Table-driven rule for mapping products into a high-level domain
 * using canonical lowercase codes (vitamins, cleaning, baby, food, other, unknown).
 *
 * Backed by the product_domain_mapping table (V011 migration).
 *
 * NOTE: This is just the data model. No logic here.
 */
@Entity
@Table(name = "product_domain_mapping")
public class ProductDomainMappingEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Target domain, e.g. "cleaning", "baby", "food", "other", "unknown".
     * Kept as String to avoid tight coupling; DB constraint enforces valid values.
     */
    @Column(name = "domain", nullable = false, length = 32)
    private String domain;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_field", nullable = false, length = 32)
    private MatchField matchField;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_type", nullable = false, length = 32)
    private MatchType matchType;

    /**
     * Lowercased text pattern to match, e.g. "laundry detergent", "dish soap".
     */
    @Column(name = "pattern", nullable = false, columnDefinition = "text")
    private String pattern;

    /**
     * Lower number = higher priority. First active rule that matches wins.
     */
    @Column(name = "priority", nullable = false)
    private Integer priority = 100;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @Column(name = "notes", columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ─────────────────────────────────────────────────────────────
    // Enums matching the DB check constraints for match metadata
    // ─────────────────────────────────────────────────────────────

    public enum MatchField {
        CATEGORY,
        TITLE,
        BRAND,
        ALL
    }

    public enum MatchType {
        EQUALS,
        CONTAINS
    }

    // ─────────────────────────────────────────────────────────────
    // JPA lifecycle hooks
    // ─────────────────────────────────────────────────────────────

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    // ─────────────────────────────────────────────────────────────
    // Getters / setters
    // ─────────────────────────────────────────────────────────────

    public Long getId() {
        return id;
    }

    public String getDomain() {
        return domain;
    }

    public void setDomain(String domain) {
        this.domain = domain;
    }

    public MatchField getMatchField() {
        return matchField;
    }

    public void setMatchField(MatchField matchField) {
        this.matchField = matchField;
    }

    public MatchType getMatchType() {
        return matchType;
    }

    public void setMatchType(MatchType matchType) {
        this.matchType = matchType;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
