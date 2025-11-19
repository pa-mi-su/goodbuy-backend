ALTER TABLE ingredient_missing_report
    ADD CONSTRAINT ux_missing_ingredient_name_ean
        UNIQUE (ingredient_name, product_ean);
