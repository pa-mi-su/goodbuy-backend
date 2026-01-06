-- V12__seed_vitamins_domain_rules.sql
-- STRICT: VITAMINS domain only.
-- Matches ONLY vitamin keywords (no supplements, no dosage forms).
-- This seed ENFORCES that only VITAMINS rules exist.

-- Dev-safe reset: remove ALL existing rules so only vitamins rules remain.
-- If you later want multiple domains, change this to DELETE WHERE domain='VITAMINS'
-- and create separate seed migrations per domain.
DELETE FROM product_domain_mapping;

INSERT INTO product_domain_mapping
    (domain, match_field, match_type, pattern, priority, active, notes, created_at, updated_at)
VALUES
-- ─────────────────────────────────────────────────────────────
-- 1) TITLE rules (highest confidence)
-- ─────────────────────────────────────────────────────────────
('VITAMINS', 'TITLE', 'CONTAINS', 'multivitamin',  1, true,
 'TITLE contains multivitamin → VITAMINS domain.',
 now(), now()),

('VITAMINS', 'TITLE', 'CONTAINS', 'vitamins',      2, true,
 'TITLE contains vitamins → VITAMINS domain.',
 now(), now()),

('VITAMINS', 'TITLE', 'CONTAINS', 'vitamin',       3, true,
 'TITLE contains vitamin → VITAMINS domain.',
 now(), now()),

-- ─────────────────────────────────────────────────────────────
-- 2) CATEGORY rules
-- ─────────────────────────────────────────────────────────────
('VITAMINS', 'CATEGORY', 'CONTAINS', 'multivitamin', 10, true,
 'CATEGORY contains multivitamin → VITAMINS domain.',
 now(), now()),

('VITAMINS', 'CATEGORY', 'CONTAINS', 'vitamins',     11, true,
 'CATEGORY contains vitamins → VITAMINS domain.',
 now(), now()),

('VITAMINS', 'CATEGORY', 'CONTAINS', 'vitamin',      12, true,
 'CATEGORY contains vitamin → VITAMINS domain.',
 now(), now());
