CREATE TABLE IF NOT EXISTS ingredients (
    id              BIGSERIAL PRIMARY KEY,
    canonical_key   VARCHAR(255) NOT NULL UNIQUE,
    display_name    VARCHAR(255) NOT NULL,
    summary         TEXT,
    description     TEXT,
    function        TEXT,
    concerns        TEXT,
    safety_score    NUMERIC(4,2),
    rating_letter   VARCHAR(4),
    references_count INT,
    category        VARCHAR(255),
    regulation_notes TEXT,
    is_active       BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS ingredient_alias (
    id          BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    alias       VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_ingredient_alias_lookup
    ON ingredient_alias (alias);
