package app.goodbuy.adapters.core.products.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Stored whenever a user reports a missing product
 * (e.g. "Help catalog this product").
 *
 * This lets us:
 *  - See which EANs users care about
 *  - Backfill them into our catalog/ingredients DB
 *  - Audit when/how they were reported
 */
@Entity
@Table(name = "product_missing_report")
public class MissingProductReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * The scanned product code. EAN/GTIN/UPC etc.
     */
    @Column(nullable = false, length = 32)
    private String ean;

    /**
     * Optional free-text name as seen in the client (if any).
     */
    @Column(length = 255)
    private String productName;

    /**
     * Optional brand string.
     */
    @Column(length = 255)
    private String brand;

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

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = ean;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }

    public String getBrand() {
        return brand;
    }

    public void setBrand(String brand) {
        this.brand = brand;
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
