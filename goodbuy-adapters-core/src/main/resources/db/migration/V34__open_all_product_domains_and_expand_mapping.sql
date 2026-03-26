DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'product_domain_config_domain_chk'
    ) THEN
        ALTER TABLE product_domain_config DROP CONSTRAINT product_domain_config_domain_chk;
    END IF;
END
$$;

ALTER TABLE product_domain_config
    ADD CONSTRAINT product_domain_config_domain_chk
    CHECK (domain IN ('unknown','vitamins','medicine','cleaning','personal-care','baby','food','household','other'));

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
    CHECK (domain IN ('vitamins','medicine','cleaning','personal-care','baby','food','household','other','unknown'));

INSERT INTO product_domain_config (domain, is_enabled, is_rated)
VALUES
    ('vitamins', TRUE, TRUE),
    ('medicine', TRUE, TRUE),
    ('cleaning', TRUE, TRUE),
    ('personal-care', TRUE, TRUE),
    ('baby', TRUE, TRUE),
    ('food', TRUE, TRUE),
    ('household', TRUE, TRUE),
    ('other', TRUE, TRUE),
    ('unknown', TRUE, TRUE)
ON CONFLICT (domain) DO UPDATE
SET is_enabled = EXCLUDED.is_enabled,
    is_rated = EXCLUDED.is_rated,
    updated_at = now();

INSERT INTO product_domain_mapping (match_field, match_type, pattern, domain, priority, active, notes)
SELECT * FROM (
    VALUES
        ('TITLE', 'CONTAINS', 'drug facts', 'medicine', 8, TRUE, 'AI expansion: medicine'),
        ('TITLE', 'CONTAINS', 'acetaminophen', 'medicine', 9, TRUE, 'AI expansion: medicine'),
        ('TITLE', 'CONTAINS', 'ibuprofen', 'medicine', 10, TRUE, 'AI expansion: medicine'),
        ('TITLE', 'CONTAINS', 'multivitamin', 'vitamins', 11, TRUE, 'AI expansion: vitamins'),
        ('TITLE', 'CONTAINS', 'supplement', 'vitamins', 12, TRUE, 'AI expansion: vitamins'),
        ('TITLE', 'CONTAINS', 'shampoo', 'personal-care', 13, TRUE, 'AI expansion: personal care'),
        ('TITLE', 'CONTAINS', 'conditioner', 'personal-care', 14, TRUE, 'AI expansion: personal care'),
        ('TITLE', 'CONTAINS', 'body wash', 'personal-care', 15, TRUE, 'AI expansion: personal care'),
        ('TITLE', 'CONTAINS', 'soap', 'personal-care', 16, TRUE, 'AI expansion: personal care'),
        ('TITLE', 'CONTAINS', 'lotion', 'personal-care', 17, TRUE, 'AI expansion: personal care'),
        ('TITLE', 'CONTAINS', 'moisturizer', 'personal-care', 18, TRUE, 'AI expansion: personal care'),
        ('TITLE', 'CONTAINS', 'deodorant', 'personal-care', 19, TRUE, 'AI expansion: personal care'),
        ('TITLE', 'CONTAINS', 'detergent', 'cleaning', 20, TRUE, 'AI expansion: cleaning'),
        ('TITLE', 'CONTAINS', 'disinfect', 'cleaning', 21, TRUE, 'AI expansion: cleaning'),
        ('TITLE', 'CONTAINS', 'cleaner', 'cleaning', 22, TRUE, 'AI expansion: cleaning'),
        ('TITLE', 'CONTAINS', 'bleach', 'cleaning', 23, TRUE, 'AI expansion: cleaning'),
        ('TITLE', 'CONTAINS', 'baby', 'baby', 24, TRUE, 'AI expansion: baby'),
        ('TITLE', 'CONTAINS', 'household', 'household', 25, TRUE, 'AI expansion: household'),
        ('CATEGORY', 'CONTAINS', 'medicine', 'medicine', 26, TRUE, 'AI expansion: medicine'),
        ('CATEGORY', 'CONTAINS', 'otc', 'medicine', 27, TRUE, 'AI expansion: medicine'),
        ('CATEGORY', 'CONTAINS', 'personal care', 'personal-care', 28, TRUE, 'AI expansion: personal care'),
        ('CATEGORY', 'CONTAINS', 'beauty', 'personal-care', 29, TRUE, 'AI expansion: personal care'),
        ('CATEGORY', 'CONTAINS', 'soap', 'personal-care', 30, TRUE, 'AI expansion: personal care'),
        ('CATEGORY', 'CONTAINS', 'household', 'household', 31, TRUE, 'AI expansion: household'),
        ('CATEGORY', 'CONTAINS', 'food', 'food', 32, TRUE, 'AI expansion: food'),
        ('CATEGORY', 'CONTAINS', 'beverage', 'food', 33, TRUE, 'AI expansion: food')
) AS v(match_field, match_type, pattern, domain, priority, active, notes)
WHERE NOT EXISTS (
    SELECT 1
    FROM product_domain_mapping m
    WHERE lower(m.match_field) = lower(v.match_field)
      AND lower(m.match_type) = lower(v.match_type)
      AND lower(m.pattern) = lower(v.pattern)
      AND lower(m.domain) = lower(v.domain)
);
