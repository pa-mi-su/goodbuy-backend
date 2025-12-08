-- V13__create_ingredient_signals.sql
BEGIN;

CREATE TABLE IF NOT EXISTS ingredient_signals (
    ingredient_id BIGINT PRIMARY KEY REFERENCES ingredients(id) ON DELETE CASCADE,
    iarc_group SMALLINT,
    prop65_listed BOOLEAN NOT NULL DEFAULT FALSE,
    ewg_score SMALLINT,
    eu_prohibited BOOLEAN NOT NULL DEFAULT FALSE,
    eu_restricted BOOLEAN NOT NULL DEFAULT FALSE,
    pubchem_mutagen BOOLEAN NOT NULL DEFAULT FALSE,
    pubchem_reproductive_toxin BOOLEAN NOT NULL DEFAULT FALSE,
    epa_chronic_toxicity BOOLEAN NOT NULL DEFAULT FALSE,
    skin_irritant BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMIT;
