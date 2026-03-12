UPDATE product_domain_mapping
SET domain = lower(domain)
WHERE domain IS NOT NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_domain_mapping_domain_chk'
    ) THEN
        ALTER TABLE product_domain_mapping DROP CONSTRAINT product_domain_mapping_domain_chk;
    END IF;
END
$$;

ALTER TABLE product_domain_mapping
    ADD CONSTRAINT product_domain_mapping_domain_chk
    CHECK (domain IN ('vitamins', 'cleaning', 'baby', 'food', 'other', 'unknown'));
