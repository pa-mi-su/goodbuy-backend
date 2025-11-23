-- V6__add_favorites.sql
-- Users can save (favorite) products

CREATE TABLE IF NOT EXISTS favorite_product (
    id         BIGSERIAL PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,
    ean        VARCHAR(32) NOT NULL,
    saved_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One favorite per product per user
ALTER TABLE favorite_product
    ADD CONSTRAINT ux_fav_user_ean
        UNIQUE (user_id, ean);

-- Fast query: list a user's favorites newest first
CREATE INDEX IF NOT EXISTS idx_fav_user_saved
    ON favorite_product (user_id, saved_at DESC);
