-- Enable cleaning as a supported, rated domain and add DB-driven mapping rules.

UPDATE product_domain_config
SET is_enabled = TRUE,
    is_rated = TRUE,
    updated_at = now()
WHERE domain = 'cleaning';

DELETE FROM product_domain_mapping
WHERE domain = 'cleaning';

INSERT INTO product_domain_mapping
    (domain, match_field, match_type, pattern, priority, active, notes, created_at, updated_at)
VALUES
('cleaning', 'TITLE',    'CONTAINS', 'all-purpose cleaner',  1, TRUE, 'TITLE contains all-purpose cleaner -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'multipurpose cleaner',  2, TRUE, 'TITLE contains multipurpose cleaner -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'glass cleaner',         3, TRUE, 'TITLE contains glass cleaner -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'bathroom cleaner',      4, TRUE, 'TITLE contains bathroom cleaner -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'toilet bowl cleaner',   5, TRUE, 'TITLE contains toilet bowl cleaner -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'dish soap',             6, TRUE, 'TITLE contains dish soap -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'dishwashing liquid',    7, TRUE, 'TITLE contains dishwashing liquid -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'laundry detergent',     8, TRUE, 'TITLE contains laundry detergent -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'fabric softener',       9, TRUE, 'TITLE contains fabric softener -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'stain remover',        10, TRUE, 'TITLE contains stain remover -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'degreaser',            11, TRUE, 'TITLE contains degreaser -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'disinfecting wipes',   12, TRUE, 'TITLE contains disinfecting wipes -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'sanitizing wipes',     13, TRUE, 'TITLE contains sanitizing wipes -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'bleach',               14, TRUE, 'TITLE contains bleach -> cleaning.', now(), now()),
('cleaning', 'TITLE',    'CONTAINS', 'disinfectant',         15, TRUE, 'TITLE contains disinfectant -> cleaning.', now(), now()),

('cleaning', 'CATEGORY', 'CONTAINS', 'cleaning',             30, TRUE, 'CATEGORY contains cleaning -> cleaning.', now(), now()),
('cleaning', 'CATEGORY', 'CONTAINS', 'household',            31, TRUE, 'CATEGORY contains household -> cleaning.', now(), now()),
('cleaning', 'CATEGORY', 'CONTAINS', 'detergent',            32, TRUE, 'CATEGORY contains detergent -> cleaning.', now(), now()),
('cleaning', 'CATEGORY', 'CONTAINS', 'dishwashing',          33, TRUE, 'CATEGORY contains dishwashing -> cleaning.', now(), now()),
('cleaning', 'CATEGORY', 'CONTAINS', 'laundry',              34, TRUE, 'CATEGORY contains laundry -> cleaning.', now(), now()),
('cleaning', 'CATEGORY', 'CONTAINS', 'disinfectant',         35, TRUE, 'CATEGORY contains disinfectant -> cleaning.', now(), now()),

('cleaning', 'ALL',      'CONTAINS', 'cleaner',              50, TRUE, 'Fallback cleaner keyword -> cleaning.', now(), now()),
('cleaning', 'ALL',      'CONTAINS', 'detergent',            51, TRUE, 'Fallback detergent keyword -> cleaning.', now(), now()),
('cleaning', 'ALL',      'CONTAINS', 'degreaser',            52, TRUE, 'Fallback degreaser keyword -> cleaning.', now(), now()),
('cleaning', 'ALL',      'CONTAINS', 'disinfectant',         53, TRUE, 'Fallback disinfectant keyword -> cleaning.', now(), now()),
('cleaning', 'ALL',      'CONTAINS', 'sanitizer',            54, TRUE, 'Fallback sanitizer keyword -> cleaning.', now(), now());
