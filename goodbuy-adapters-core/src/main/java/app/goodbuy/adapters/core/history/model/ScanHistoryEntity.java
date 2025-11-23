package app.goodbuy.adapters.core.history.model;

import app.goodbuy.adapters.core.products.model.ProductEntity;
import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the 'scan_history' table.
 *
 * Expected Flyway V5__add_scan_history.sql:
 *  - id BIGSERIAL PRIMARY KEY
 *  - user_id UUID NOT NULL REFERENCES app_user(id)
 *  - product_id BIGINT REFERENCES products(id)
 *  - ean VARCHAR(32) NOT NULL
 *  - product_name VARCHAR(255)
 *  - brand VARCHAR(255)
 *  - scanned_at TIMESTAMPTZ NOT NULL DEFAULT now()
 */
@Entity
@Table(name = "scan_history")
public class ScanHistoryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // FK to app_user.id, but as raw UUID (no JPA relation)
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    private ProductEntity product;

    @Column(name = "ean", nullable = false, length = 32)
    private String ean;

    @Column(name = "product_name")
    private String productName;

    @Column(name = "brand")
    private String brand;

    @Column(name = "scanned_at", nullable = false, updatable = false)
    private OffsetDateTime scannedAt;

    // ─────────────────────────────
    // Constructors
    // ─────────────────────────────

    protected ScanHistoryEntity() {
        // JPA
    }

    public ScanHistoryEntity(UUID userId, String ean) {
        this.userId = userId;
        this.ean = ean;
        this.scannedAt = OffsetDateTime.now();
    }

    // ─────────────────────────────
    // Getters / setters
    // ─────────────────────────────

    public Long getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public ProductEntity getProduct() {
        return product;
    }

    public void setProduct(ProductEntity product) {
        this.product = product;
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

    public OffsetDateTime getScannedAt() {
        return scannedAt;
    }

    public void setScannedAt(OffsetDateTime scannedAt) {
        this.scannedAt = scannedAt;
    }
}
