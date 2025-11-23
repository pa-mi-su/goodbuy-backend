package app.goodbuy.adapters.core.favorites.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * JPA entity for the 'favorite_product' table.
 *
 * Matches:
 *  V6__add_favorites.sql
 *  V7__extend_favorite_product_snapshot.sql
 */
@Entity
@Table(name = "favorite_product")
public class FavoriteEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // user_id UUID NOT NULL
    @Column(name = "user_id", nullable = false)
    private UUID userId;

    // ean VARCHAR(32) NOT NULL
    @Column(name = "ean", nullable = false, length = 32)
    private String ean;

    // saved_at TIMESTAMPTZ NOT NULL DEFAULT now()
    @Column(name = "saved_at", nullable = false, updatable = false)
    private OffsetDateTime savedAt;

    // Snapshot fields (from V7):
    @Column(name = "product_name")
    private String productName;

    @Column(name = "brand")
    private String brand;

    @Column(name = "rating_letter", length = 4)
    private String ratingLetter;

    @Column(name = "safety_score")
    private Double safetyScore;

    // ─────────────────────────────
    // Constructors
    // ─────────────────────────────

    protected FavoriteEntity() {
        // for JPA
    }

    public FavoriteEntity(UUID userId, String ean) {
        this.userId = userId;
        this.ean = ean;
    }

    // ─────────────────────────────
    // Lifecycle
    // ─────────────────────────────

    @PrePersist
    void onCreate() {
        if (this.savedAt == null) {
            this.savedAt = OffsetDateTime.now();
        }
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

    public String getEan() {
        return ean;
    }

    public void setEan(String ean) {
        this.ean = ean;
    }

    public OffsetDateTime getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(OffsetDateTime savedAt) {
        this.savedAt = savedAt;
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

    public String getRatingLetter() {
        return ratingLetter;
    }

    public void setRatingLetter(String ratingLetter) {
        this.ratingLetter = ratingLetter;
    }

    public Double getSafetyScore() {
        return safetyScore;
    }

    public void setSafetyScore(Double safetyScore) {
        this.safetyScore = safetyScore;
    }
}
