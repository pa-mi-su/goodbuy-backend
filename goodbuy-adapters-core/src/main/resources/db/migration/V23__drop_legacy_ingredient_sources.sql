-- V23__drop_legacy_ingredient_sources.sql
-- Only after you confirm nothing reads/writes it anymore.
DROP TABLE IF EXISTS ingredient_sources CASCADE;
