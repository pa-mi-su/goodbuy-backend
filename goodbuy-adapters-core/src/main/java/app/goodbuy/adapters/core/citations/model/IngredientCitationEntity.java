package app.goodbuy.adapters.core.citations.model;

import app.goodbuy.adapters.core.ingredients.model.Ingredient;
import jakarta.persistence.*;
import java.time.OffsetDateTime;

@Entity
@Table(
        name = "ingredient_citations",
        uniqueConstraints = @UniqueConstraint(
                name = "ux_ingredient_citation_pair",
                columnNames = {"ingredient_id", "citation_id"}
        )
)
public class IngredientCitationEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", nullable = false)
    private Ingredient ingredient;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "citation_id", nullable = false)
    private CitationEntity citation;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    void onCreate() {
        if (createdAt == null) createdAt = OffsetDateTime.now();
    }

    public Long getId() { return id; }

    public Ingredient getIngredient() { return ingredient; }
    public void setIngredient(Ingredient ingredient) { this.ingredient = ingredient; }

    public CitationEntity getCitation() { return citation; }
    public void setCitation(CitationEntity citation) { this.citation = citation; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(OffsetDateTime createdAt) { this.createdAt = createdAt; }
}
