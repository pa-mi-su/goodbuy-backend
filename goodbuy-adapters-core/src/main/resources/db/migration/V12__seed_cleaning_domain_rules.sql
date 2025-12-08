-- V12__seed_cleaning_domain_rules.sql
-- Seed initial product_domain_mapping rules for CLEANING domain

INSERT INTO product_domain_mapping
    (domain, match_field, match_type, pattern, priority, active, notes, created_at, updated_at)
VALUES
-- ─────────────────────────────────────────────────────────────
-- 1) Baking soda → CLEANING (strong, specific rule)
-- ─────────────────────────────────────────────────────────────
('CLEANING', 'TITLE',   'CONTAINS', 'baking soda', 1,  true,
 'Treat any product whose title mentions "baking soda" as a cleaning product.',
 now(), now()),

('CLEANING', 'CATEGORY','CONTAINS', 'baking soda', 2,  true,
 'If category itself mentions baking soda, treat as cleaning (e.g., "baking soda & cleaning").',
 now(), now()),

-- ─────────────────────────────────────────────────────────────
-- 2) Laundry detergents / laundry cleaners
-- ─────────────────────────────────────────────────────────────
('CLEANING', 'CATEGORY','CONTAINS', 'laundry detergent', 10, true,
 'Laundry detergents (category) are cleaning products.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'laundry detergent', 11, true,
 'Laundry detergents (title) are cleaning products.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'laundry soap',      12, true,
 'Laundry soap bars/liquids are cleaning products.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'fabric softener',   13, true,
 'Fabric softeners go with laundry cleaning.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'stain remover',     14, true,
 'Stain removers are cleaning products.',
 now(), now()),

-- catch general "detergent" after the more specific ones
('CLEANING', 'TITLE',   'CONTAINS', 'detergent',         15, true,
 'Generic detergent in the title is assumed to be a cleaning product.',
 now(), now()),

-- ─────────────────────────────────────────────────────────────
-- 3) Dishwashing / dish soap / dish cleaners
-- ─────────────────────────────────────────────────────────────
('CLEANING', 'CATEGORY','CONTAINS', 'dishwashing',       20, true,
 'Dishwashing category → cleaning.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'dishwashing liquid',21, true,
 'Dishwashing liquid is a cleaning product.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'dishwashing detergent',22, true,
 'Dishwashing detergent is a cleaning product.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'dish soap',         23, true,
 'Dish soap is a cleaning product.',
 now(), now()),

-- ─────────────────────────────────────────────────────────────
-- 4) All-purpose / surface cleaners
-- ─────────────────────────────────────────────────────────────
('CLEANING', 'TITLE',   'CONTAINS', 'all purpose cleaner', 30, true,
 'Explicit all-purpose cleaner products.',
 now(), now()),

('CLEANING', 'CATEGORY','CONTAINS', 'all purpose cleaner', 31, true,
 'All-purpose cleaner category.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'surface cleaner',     32, true,
 'Surface cleaners are cleaning products.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'multi-surface cleaner',33, true,
 'Multi-surface cleaners are cleaning products.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'all-purpose cleaner', 34, true,
 'Hyphenated variant of all-purpose cleaner.',
 now(), now()),

-- ─────────────────────────────────────────────────────────────
-- 5) Bathroom / toilet / glass
-- ─────────────────────────────────────────────────────────────
('CLEANING', 'TITLE',   'CONTAINS', 'bathroom cleaner',   40, true,
 'Bathroom cleaners.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'toilet bowl',        41, true,
 'Toilet bowl cleaners.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'glass cleaner',      42, true,
 'Glass cleaners.',
 now(), now()),

-- ─────────────────────────────────────────────────────────────
-- 6) Disinfectants / bleach
-- ─────────────────────────────────────────────────────────────
('CLEANING', 'CATEGORY','CONTAINS', 'disinfectant',       50, true,
 'Disinfectant category.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'disinfectant',       51, true,
 'Disinfectant appears in title.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'bleach',             52, true,
 'Bleach is a cleaning product.',
 now(), now()),

-- ─────────────────────────────────────────────────────────────
-- 7) Degreasers / descalers
-- ─────────────────────────────────────────────────────────────
('CLEANING', 'TITLE',   'CONTAINS', 'degreaser',          60, true,
 'Degreasers are cleaning products.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'descaler',           61, true,
 'Descalers are cleaning products.',
 now(), now()),

-- ─────────────────────────────────────────────────────────────
-- 8) Generic cleaning catch-alls (broad, low-priority)
-- ─────────────────────────────────────────────────────────────
('CLEANING', 'CATEGORY','CONTAINS', 'cleaning',           90, true,
 'Any category clearly mentioning cleaning goes to CLEANING domain.',
 now(), now()),

('CLEANING', 'TITLE',   'CONTAINS', 'cleaning',           91, true,
 'Fallback: title explicitly says "cleaning".',
 now(), now());
