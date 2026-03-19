package app.goodbuy.adapters.core.ingredients.model;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(
        name = "ingredient_missing_report",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_missing_ingredient_name_ean",
                        columnNames = {"ingredient_name", "product_ean"}
                )
        }
)
public class IngredientMissingReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ingredient_name", nullable = false, length = 255)
    private String ingredientName;

    @Column(name = "product_ean", length = 32)
    private String productEan;

    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(name = "platform", length = 32)
    private String platform;

    @Column(name = "notes")
    private String notes;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "resolved_canonical_key", length = 255)
    private String resolvedCanonicalKey;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        if (occurredAt == null) {
            occurredAt = now;
        }
        if (status == null || status.isBlank()) {
            status = "OPEN";
        }
    }

    // getters & setters

    public Long getId() {
        return id;
    }

    public String getIngredientName() {
        return ingredientName;
    }

    public void setIngredientName(String ingredientName) {
        this.ingredientName = ingredientName;
    }

    public String getProductEan() {
        return productEan;
    }

    public void setProductEan(String productEan) {
        this.productEan = productEan;
    }

    public String getAppVersion() {
        return appVersion;
    }

    public void setAppVersion(String appVersion) {
        this.appVersion = appVersion;
    }

    public String getPlatform() {
        return platform;
    }

    public void setPlatform(String platform) {
        this.platform = platform;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getResolvedCanonicalKey() {
        return resolvedCanonicalKey;
    }

    public void setResolvedCanonicalKey(String resolvedCanonicalKey) {
        this.resolvedCanonicalKey = resolvedCanonicalKey;
    }

    public Instant getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(Instant resolvedAt) {
        this.resolvedAt = resolvedAt;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(Instant occurredAt) {
        this.occurredAt = occurredAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
