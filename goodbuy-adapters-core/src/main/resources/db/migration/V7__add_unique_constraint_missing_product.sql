ALTER TABLE product_missing_report
    ADD CONSTRAINT ux_product_missing_ean UNIQUE (ean);
