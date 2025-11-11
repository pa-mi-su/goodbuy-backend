package app.goodbuy.adapters.core.products.cache;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

/**
 * JPA entity for locally cached product data.
 *
 * For now this is intentionally simple:
 *  - gtin: primary key (GTIN-14)
 *  - basic fields for quick querying
 *  - rawJson: full serialized ProductDetailDto for flexibility
 *
 * Later we can normalize images/ingredients if needed.
 */
@Entity
@Table(name = "product_cache")
public class ProductCacheEntity {

    @Id
    @Column(name = "gtin", length = 14, nullable = false, updatable = false)
    private String gtin;

    @Column(name = "name", length = 512)
    private String name;

    @Column(name = "brand", length = 256)
    private String brand;

    @Column(name = "category", length = 256)
    private String category;

    @Column(name = "source", length = 64)
    private String source; // e.g. "EAN-DB", "EAN-SEARCH"

    @Lob
    @Column(name = "raw_json", nullable = false)
    private String rawJson; // serialized ProductDetailDto

    // JPA requires a no-arg constructor
    protected ProductCacheEntity() {
    }

    public ProductCacheEntity(String gtin,
                              String name,
                              String brand,
                              String category,
                              String source,
                              String rawJson) {
        this.gtin = gtin;
        this.name = name;
        this.brand = brand;
        this.category = category;
        this.source = source;
        this.rawJson = rawJson;
    }

    public String getGtin() {
        return gtin;
    }

    public String getName() {
        return name;
    }

    public String getBrand() {
        return brand;
    }

    public String getCategory() {
        return category;
    }

    public String getSource() {
        return source;
    }

    public String getRawJson() {
        return rawJson;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setBrand(String brand) {
        this.brand = brand;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public void setSource(String source) {
        this.source = source;
    }

    public void setRawJson(String rawJson) {
        this.rawJson = rawJson;
    }
}
