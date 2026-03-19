package app.goodbuy.adapters.core.ingredients.model;

import jakarta.persistence.*;

import java.time.OffsetDateTime;

/**
 * JPA mapping for the ingredient_signals table.
 *
 * One row per Ingredient (1:1 via ingredient_id).
 *
 * NOTE:
 * We model ingredient_id as the primary key directly (no @MapsId) so adapters can
 * upsert signals using only the ingredientId. The FK is still enforced by the DB.
 */
@Entity
@Table(name = "ingredient_signals")
public class IngredientSignalsEntity {

    @Id
    @Column(name = "ingredient_id")
    private Long ingredientId;

    /**
     * Optional navigation back to Ingredient.
     * Read-only mapping to avoid forcing association assignment during inserts/updates.
     */
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "ingredient_id", insertable = false, updatable = false)
    private Ingredient ingredient;

    @Column(name = "iarc_group")
    private Integer iarcGroup;

    @Column(name = "prop65_listed", nullable = false)
    private boolean prop65Listed = false;

    @Column(name = "ewg_score")
    private Integer ewgScore;

    @Column(name = "eu_prohibited", nullable = false)
    private boolean euProhibited = false;

    @Column(name = "eu_restricted", nullable = false)
    private boolean euRestricted = false;

    @Column(name = "pubchem_mutagen", nullable = false)
    private boolean pubchemMutagen = false;

    @Column(name = "pubchem_reproductive_toxin", nullable = false)
    private boolean pubchemReproductiveToxin = false;

    @Column(name = "epa_chronic_toxicity", nullable = false)
    private boolean epaChronicToxicity = false;

    @Column(name = "skin_irritant", nullable = false)
    private boolean skinIrritant = false;

    @Column(name = "created_at", insertable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected IngredientSignalsEntity() {
        // JPA
    }

    public IngredientSignalsEntity(Long ingredientId) {
        this.ingredientId = ingredientId;
    }

    public Long getIngredientId() { return ingredientId; }
    public void setIngredientId(Long ingredientId) { this.ingredientId = ingredientId; }

    public Ingredient getIngredient() { return ingredient; }

    public Integer getIarcGroup() { return iarcGroup; }
    public void setIarcGroup(Integer iarcGroup) { this.iarcGroup = iarcGroup; }

    public boolean isProp65Listed() { return prop65Listed; }
    public void setProp65Listed(boolean prop65Listed) { this.prop65Listed = prop65Listed; }

    public Integer getEwgScore() { return ewgScore; }
    public void setEwgScore(Integer ewgScore) { this.ewgScore = ewgScore; }

    public boolean isEuProhibited() { return euProhibited; }
    public void setEuProhibited(boolean euProhibited) { this.euProhibited = euProhibited; }

    public boolean isEuRestricted() { return euRestricted; }
    public void setEuRestricted(boolean euRestricted) { this.euRestricted = euRestricted; }

    public boolean isPubchemMutagen() { return pubchemMutagen; }
    public void setPubchemMutagen(boolean pubchemMutagen) { this.pubchemMutagen = pubchemMutagen; }

    public boolean isPubchemReproductiveToxin() { return pubchemReproductiveToxin; }
    public void setPubchemReproductiveToxin(boolean pubchemReproductiveToxin) { this.pubchemReproductiveToxin = pubchemReproductiveToxin; }

    public boolean isEpaChronicToxicity() { return epaChronicToxicity; }
    public void setEpaChronicToxicity(boolean epaChronicToxicity) { this.epaChronicToxicity = epaChronicToxicity; }

    public boolean isSkinIrritant() { return skinIrritant; }
    public void setSkinIrritant(boolean skinIrritant) { this.skinIrritant = skinIrritant; }

    public OffsetDateTime getCreatedAt() { return createdAt; }
    public OffsetDateTime getUpdatedAt() { return updatedAt; }
}
