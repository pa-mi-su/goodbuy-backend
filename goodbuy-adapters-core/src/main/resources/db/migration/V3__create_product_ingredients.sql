CREATE TABLE IF NOT EXISTS product_ingredients (
    id              BIGSERIAL PRIMARY KEY,
    product_id      BIGINT NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    ingredient_id   BIGINT NOT NULL REFERENCES ingredients(id) ON DELETE CASCADE,
    display_name    VARCHAR(255),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_product_ing_product
    ON product_ingredients(product_id);

CREATE INDEX IF NOT EXISTS idx_product_ing_ingredient
    ON product_ingredients(ingredient_id);

-- Prevent duplicates
CREATE UNIQUE INDEX IF NOT EXISTS uq_prod_ing_pair
    ON product_ingredients(product_id, ingredient_id);
