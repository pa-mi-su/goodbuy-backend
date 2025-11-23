package app.goodbuy.adapters.core.products.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // EAN / GTIN – this is our natural key
    @Column(name = "ean", nullable = false, unique = true, length = 32)
    private String ean;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "brand", length = 255)
    private String brand;

    @Column(name = "category", length = 255)
    private String category;

    @Column(name = "description")
    private String description;

    @Column(name = "primary_image_url")
    private String primaryImageUrl;

    /** S3 URL for EAN-DB or user-uploaded product image */
    @Column(name = "primary_image_s3_url")
    private String primaryImageS3Url;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    // ───────── getters & setters ─────────

    public Long getId() { return id; }

    public String getEan() { return ean; }
    public void setEan(String ean) { this.ean = ean; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getBrand() { return brand; }
    public void setBrand(String brand) { this.brand = brand; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPrimaryImageUrl() { return primaryImageUrl; }
    public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }

    public String getPrimaryImageS3Url() { return primaryImageS3Url; }
    public void setPrimaryImageS3Url(String primaryImageS3Url) { this.primaryImageS3Url = primaryImageS3Url; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
