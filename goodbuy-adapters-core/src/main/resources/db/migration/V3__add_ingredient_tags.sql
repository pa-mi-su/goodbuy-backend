-- V3__add_ingredient_tags.sql
-- Add tags for ingredients to match JPA mapping

CREATE TABLE IF NOT EXISTS ingredient_tags (
    id            BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    name          VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- One given tag-name only once per ingredient
ALTER TABLE ingredient_tags
    ADD CONSTRAINT ux_ingredient_tag_per_ing
        UNIQUE (ingredient_id, name);

-- Lookup by ingredient
CREATE INDEX IF NOT EXISTS idx_ingredient_tags_ingredient_id
    ON ingredient_tags (ingredient_id);

-- Optional: free-text / filtering by tag name
CREATE INDEX IF NOT EXISTS idx_ingredient_tags_name
    ON ingredient_tags (name);
