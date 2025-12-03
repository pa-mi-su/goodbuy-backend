-- V15__create_ingredient_signals.sql
-- Store authoritative “signals” that feed the IngredientScoringEngine.
-- One row per ingredient (1:1 with ingredients.id).

BEGIN;

CREATE TABLE IF NOT EXISTS ingredient_signals (
    -- 1:1 link to ingredients
    ingredient_id BIGINT PRIMARY KEY
        REFERENCES ingredients(id)
        ON DELETE CASCADE,

    -- ─────────────────────────────────────────────
    -- Core hazard / regulatory signals
    -- ─────────────────────────────────────────────

    -- IARC carcinogenicity group:
    --   1  = Group 1 (carcinogenic to humans)
    --   2  = Group 2A/2B (we normalize into a single "2" bucket)
    --   3+ = lower confidence groups, if applicable
    iarc_group SMALLINT,

    -- California Proposition 65 listing (true if listed for any endpoint)
    prop65_listed BOOLEAN NOT NULL DEFAULT FALSE,

    -- EWG numeric score (1–10, higher = more concern)
    ewg_score SMALLINT,

    -- EU cosmetic / chemical status (simplified flags)
    eu_prohibited BOOLEAN NOT NULL DEFAULT FALSE,
    eu_restricted BOOLEAN NOT NULL DEFAULT FALSE,

    -- PubChem / GHS style hazard flags
    pubchem_mutagen              BOOLEAN NOT NULL DEFAULT FALSE,
    pubchem_reproductive_toxin   BOOLEAN NOT NULL DEFAULT FALSE,

    -- EPA chronic toxicity signal (true if flagged in key lists)
    epa_chronic_toxicity BOOLEAN NOT NULL DEFAULT FALSE,

    -- Irritation / sensitization (skin/eye/respiratory)
    skin_irritant BOOLEAN NOT NULL DEFAULT FALSE,

    -- ─────────────────────────────────────────────
    -- Timestamps
    -- ─────────────────────────────────────────────
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
