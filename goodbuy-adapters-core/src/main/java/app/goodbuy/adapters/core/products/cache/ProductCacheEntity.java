package app.goodbuy.adapters.core.products.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "product_cache")
public class ProductCacheEntity {

    @Id
    @Column(name = "gtin", nullable = false, length = 32)
    private String gtin;

    @Column(name = "json_payload", nullable = false, columnDefinition = "TEXT")
    private String jsonPayload;

    @Column(name = "source", length = 64)
    private String source;

    // Handled by DB default (NOW()) — not by JPA
    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    // Handled by DB trigger (updated_at = NOW()) — not by JPA
    @Column(name = "updated_at", insertable = false, updatable = false)
    private Instant updatedAt;

    // ── Getters & Setters ───────────────────────────────────────────────

    public String getGtin() {
        return gtin;
    }

    public void setGtin(String gtin) {
        this.gtin = gtin;
    }

    public String getJsonPayload() {
        return jsonPayload;
    }

    public void setJsonPayload(String jsonPayload) {
        this.jsonPayload = jsonPayload;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
