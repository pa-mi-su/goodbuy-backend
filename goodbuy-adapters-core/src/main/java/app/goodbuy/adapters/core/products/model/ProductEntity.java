package app.goodbuy.adapters.core.products.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Entity
@Table(name = "products")
public class ProductEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "ean", nullable = false, unique = true, length = 32)
    private String ean;

    @Column(name = "name", length = 255)
    private String name;

    @Column(name = "brand", length = 255)
    private String brand;

    @Column(name = "category", length = 255)
    private String category;

    /**
     * High-level domain for product:
     *   "cleaning", "baby", "food", etc.
     *   Default in DB is "unknown".
     *
     * IMPORTANT:
     *   We NEVER persist null here. If callers pass null/blank,
     *   we store "unknown" so Postgres NOT NULL is happy and
     *   the DB default is effectively enforced on the Java side.
     */
    @Column(name = "domain", nullable = false, length = 64)
    private String domain = "unknown";   // Java-side default

    @Column(name = "description")
    private String description;

    @Column(name = "primary_image_url")
    private String primaryImageUrl;

    /** S3 URL for EAN-DB or user-uploaded product image */
    @Column(name = "primary_image_s3_url")
    private String primaryImageS3Url;

    @Column(name = "raw_ingredient_text")
    private String rawIngredientText;

    /**
     * Overall GoodBuy safety score for this product.
     *
     * Same 0–99 scale as ingredients.safety_score (NUMERIC(4,2)).
     * Null means "not yet scored".
     */
    @Column(name = "safety_score")
    private BigDecimal safetyScore;

    /**
     * Overall rating letter for this product (A–F).
     *
     * Null means "not yet scored".
     */
    @Column(name = "rating_letter", length = 4)
    private String ratingLetter;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    @OneToMany(mappedBy = "product", fetch = FetchType.LAZY)
    private List<ProductIngredientEntity> productIngredients = new ArrayList<>();

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

    public String getDomain() { return domain; }

    public void setDomain(String domain) {
        if (domain == null || domain.isBlank()) {
            this.domain = "unknown";
        } else {
            this.domain = domain.trim().toLowerCase(Locale.ROOT);
        }
    }

    @PrePersist
    public void prePersist() {
        if (this.domain == null || this.domain.isBlank()) {
            this.domain = "unknown";
        } else {
            this.domain = this.domain.trim().toLowerCase(Locale.ROOT);
        }
    }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getPrimaryImageUrl() { return primaryImageUrl; }
    public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }

    public String getPrimaryImageS3Url() { return primaryImageS3Url; }
    public void setPrimaryImageS3Url(String primaryImageS3Url) { this.primaryImageS3Url = primaryImageS3Url; }

    public String getRawIngredientText() { return rawIngredientText; }
    public void setRawIngredientText(String rawIngredientText) { this.rawIngredientText = rawIngredientText; }

    public BigDecimal getSafetyScore() { return safetyScore; }
    public void setSafetyScore(BigDecimal safetyScore) { this.safetyScore = safetyScore; }

    public String getRatingLetter() { return ratingLetter; }
    public void setRatingLetter(String ratingLetter) { this.ratingLetter = ratingLetter; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public OffsetDateTime getUpdatedAt() { return updatedAt; }

    public List<ProductIngredientEntity> getProductIngredients() {
        return productIngredients;
    }

    public void setProductIngredients(List<ProductIngredientEntity> productIngredients) {
        this.productIngredients = productIngredients;
    }
}
