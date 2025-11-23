package app.goodbuy.adapters.core.ingredients.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Stored whenever a user reports a missing ingredient.
 * This allows:
 *  - Slack/email alerts
 *  - Backlog review for manual ingestion into GoodBuy Ingredient DB
 *  - Audit/history tracking
 */
@Entity
@Table(name = "ingredient_missing_report")
public class MissingIngredientReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String ingredientName;

    @Column(length = 32)
    private String productEan; // may be null if user reports outside a product context

    @Column(length = 64)
    private String appVersion;

    @Column(length = 32)
    private String platform; // iOS, Android, etc.

    @Column(columnDefinition = "text")
    private String notes;

    @Column(nullable = false)
    private OffsetDateTime occurredAt;

    @Column(nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (occurredAt == null) {
            occurredAt = createdAt;
        }
    }

    // ───── getters & setters ─────

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

    public OffsetDateTime getOccurredAt() {
        return occurredAt;
    }

    public void setOccurredAt(OffsetDateTime occurredAt) {
        this.occurredAt = occurredAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
