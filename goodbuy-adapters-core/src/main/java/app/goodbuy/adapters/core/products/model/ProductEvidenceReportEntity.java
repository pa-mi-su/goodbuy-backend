package app.goodbuy.adapters.core.products.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

/**
 * Global evidence that a product has already been reported
 * for a specific reason (missing_product, unclear_ingredients).
 *
 * Backed by table: product_evidence_report
 *
 * Dedupe rule (ENFORCED BY DB):
 *   ONE ROW PER (ean, reason)
 *
 * This table ALSO stores S3 URLs for user-submitted photos
 * when reason = missing_product.
 */
@Entity
@Table(
        name = "product_evidence_report",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "ux_product_evidence_ean_reason",
                        columnNames = {"ean", "reason"}
                )
        }
)
public class ProductEvidenceReportEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Normalized GTIN (digits only, stored as GTIN-14) */
    @Column(nullable = false, length = 32)
    private String ean;

    /**
     * Reason this evidence exists.
     *
     * Expected values:
     *  - missing_product
     *  - unclear_ingredients
     */
    @Column(nullable = false, length = 64)
    private String reason;

    /**
     * Status of the report record.
     *
     * DB default: 'REPORTED'
     * Expected values (initially):
     *  - REPORTED
     *
     * (We can expand later: IN_PROGRESS, CLASSIFIED, IGNORED, RESOLVED, etc.)
     */
    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "product_name", length = 255)
    private String productName;

    @Column(length = 255)
    private String brand;

    @Column(name = "app_version", length = 64)
    private String appVersion;

    @Column(length = 32)
    private String platform;

    @Column(columnDefinition = "text")
    private String notes;

    // S3-hosted user images (used for missing_product)
    @Column(name = "front_image_s3_url", columnDefinition = "text")
    private String frontImageS3Url;

    @Column(name = "back_image_s3_url", columnDefinition = "text")
    private String backImageS3Url;

    @Column(name = "ocr_status", nullable = false, length = 32)
    private String ocrStatus;

    @Column(name = "ocr_provider", length = 64)
    private String ocrProvider;

    @Column(name = "ocr_raw_text", columnDefinition = "text")
    private String ocrRawText;

    @Column(name = "parsed_ingredient_text", columnDefinition = "text")
    private String parsedIngredientText;

    @Column(name = "last_reprocessed_at")
    private OffsetDateTime lastReprocessedAt;

    /** Last time this evidence was observed/reported */
    @Column(name = "occurred_at", nullable = false)
    private OffsetDateTime occurredAt;

    /** First time this evidence was created */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (occurredAt == null) occurredAt = now;
        if (status == null || status.isBlank()) status = "REPORTED";
        if (ocrStatus == null || ocrStatus.isBlank()) ocrStatus = "NOT_REQUESTED";
    }

    // ───── getters & setters ─────

    public Long getId() { return id; }

    public String getEan() { return ean; }
    public void setEan(String ean) { this.ean = ean; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getProductName() { return productName; }
    public void setProductName(String productName) { this.productName = productName; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getAppVersion() { return appVersion; }
    public void setAppVersion(String appVersion) { this.appVersion = appVersion; }

    public String getPlatform() { return platform; }
    public void setPlatform(String platform) { this.platform = platform; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public String getFrontImageS3Url() { return frontImageS3Url; }
    public void setFrontImageS3Url(String frontImageS3Url) { this.frontImageS3Url = frontImageS3Url; }

    public String getBackImageS3Url() { return backImageS3Url; }
    public void setBackImageS3Url(String backImageS3Url) { this.backImageS3Url = backImageS3Url; }

    public String getOcrStatus() { return ocrStatus; }
    public void setOcrStatus(String ocrStatus) { this.ocrStatus = ocrStatus; }

    public String getOcrProvider() { return ocrProvider; }
    public void setOcrProvider(String ocrProvider) { this.ocrProvider = ocrProvider; }

    public String getOcrRawText() { return ocrRawText; }
    public void setOcrRawText(String ocrRawText) { this.ocrRawText = ocrRawText; }

    public String getParsedIngredientText() { return parsedIngredientText; }
    public void setParsedIngredientText(String parsedIngredientText) { this.parsedIngredientText = parsedIngredientText; }

    public OffsetDateTime getLastReprocessedAt() { return lastReprocessedAt; }
    public void setLastReprocessedAt(OffsetDateTime lastReprocessedAt) { this.lastReprocessedAt = lastReprocessedAt; }

    public OffsetDateTime getOccurredAt() { return occurredAt; }
    public void setOccurredAt(OffsetDateTime occurredAt) { this.occurredAt = occurredAt; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
