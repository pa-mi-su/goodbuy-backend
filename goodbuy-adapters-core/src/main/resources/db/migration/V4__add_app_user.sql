-- V4__add_app_user.sql
-- Basic GoodBuy user table for email-based identity

-- Optional: if you want case-insensitive unique emails (recommended)
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE IF NOT EXISTS app_user (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email        CITEXT NOT NULL UNIQUE,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_seen_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    platform     TEXT,
    app_version  TEXT
);
