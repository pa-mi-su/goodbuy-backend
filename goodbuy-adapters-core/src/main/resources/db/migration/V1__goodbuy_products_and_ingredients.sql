-- Optional: clean up legacy table if it still exists
DROP TABLE IF EXISTS product_cache CASCADE;

-- ─────────────────────────────────────────────
-- PRODUCTS (GoodBuy master product catalog)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS products (
    id                BIGSERIAL PRIMARY KEY,
    ean               VARCHAR(32) NOT NULL UNIQUE,
    name              VARCHAR(255),
    brand             VARCHAR(255),
    category          VARCHAR(255),
    description       TEXT,
    primary_image_url TEXT,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_products_ean ON products(ean);

-- ─────────────────────────────────────────────
-- INGREDIENTS (GoodBuy master ingredient catalog)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ingredients (
    id               BIGSERIAL PRIMARY KEY,
    canonical_key    VARCHAR(255) NOT NULL UNIQUE,
    display_name     VARCHAR(255) NOT NULL,
    summary          TEXT,
    description      TEXT,

    -- keep "function" as semantic label, used for content
    "function"       TEXT,

    concerns         TEXT,
    safety_score     NUMERIC(4,2),
    rating_letter    VARCHAR(4),
    references_count INT,
    category         VARCHAR(255),
    regulation_notes TEXT,
    is_active        BOOLEAN NOT NULL DEFAULT TRUE,

    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- JPA-mapped column for functional use field
    func_use         TEXT
);

-- ─────────────────────────────────────────────
-- INGREDIENT ALIASES (fixed to match JPA entity)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ingredient_alias (
    id            BIGSERIAL PRIMARY KEY,
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    alias         VARCHAR(255) NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Unique per ingredient
ALTER TABLE ingredient_alias
    ADD CONSTRAINT ux_alias_per_ing UNIQUE (ingredient_id, alias);

-- Lookup performance
CREATE INDEX IF NOT EXISTS idx_ingredient_alias_lookup
    ON ingredient_alias (alias);

-- ─────────────────────────────────────────────
-- PRODUCT_INGREDIENTS (link GoodBuy product ↔ GoodBuy ingredient)
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS product_ingredients (
    id            BIGSERIAL PRIMARY KEY,
    product_id    BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    ingredient_id BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    display_name  VARCHAR(255),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_ing_product
    ON product_ingredients(product_id);

CREATE INDEX IF NOT EXISTS idx_product_ing_ingredient
    ON product_ingredients(ingredient_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_prod_ing_pair
    ON product_ingredients(product_id, ingredient_id);

-- ─────────────────────────────────────────────
-- MISSING INGREDIENT REPORTS
-- ─────────────────────────────────────────────
CREATE TABLE IF NOT EXISTS ingredient_missing_report (
    id              BIGSERIAL PRIMARY KEY,
    ingredient_name VARCHAR(255) NOT NULL,
    product_ean     VARCHAR(32),
    app_version     VARCHAR(64),
    platform        VARCHAR(32),
    notes           TEXT,
    occurred_at     TIMESTAMPTZ NOT NULL,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_imr_ingredient_name
    ON ingredient_missing_report(ingredient_name);

CREATE INDEX IF NOT EXISTS idx_imr_product_ean_created_at
    ON ingredient_missing_report(product_ean, created_at DESC);

ALTER TABLE ingredient_missing_report
    ADD CONSTRAINT ux_missing_ingredient_name_ean
        UNIQUE (ingredient_name, product_ean);


