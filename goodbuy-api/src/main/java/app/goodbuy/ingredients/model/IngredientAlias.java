package app.goodbuy.ingredients.model;

import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(name = "ingredient_aliases",
        uniqueConstraints = @UniqueConstraint(name = "uq_alias_per_ing", columnNames = {"ingredient_id", "alias"}),
        indexes = @Index(name = "idx_alias_ci", columnList = "alias"))
public class IngredientAlias {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false,
            foreignKey = @ForeignKey(name = "fk_alias_ingredient"))
    private Ingredient ingredient;

    @Column(name = "alias", nullable = false)
    private String alias;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        var now = OffsetDateTime.now();
        if (createdAt == null) createdAt = now;
        if (alias != null) alias = alias.trim();
    }

    // Getters
    public Long getId() { return id; }
    public Ingredient getIngredient() { return ingredient; }
    public String getAlias() { return alias; }
    public OffsetDateTime getCreatedAt() { return createdAt; }
}
