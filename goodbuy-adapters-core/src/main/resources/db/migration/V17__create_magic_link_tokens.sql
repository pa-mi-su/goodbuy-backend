-- V17__create_magic_link_tokens.sql
-- Table for one-time, expiring magic-link tokens used for passwordless login.

CREATE TABLE magic_link_token (
    id           UUID PRIMARY KEY,
    user_id      UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,

    -- Opaque, random token that will be embedded in the emailed link
    token        VARCHAR(255) NOT NULL UNIQUE,

    -- When the token was created
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    -- When the token expires (e.g. 15–30 minutes after creation)
    expires_at   TIMESTAMPTZ NOT NULL,

    -- When the token was consumed; NULL = not used yet
    used_at      TIMESTAMPTZ,

    -- Optional metadata for audit / abuse detection
    ip_address   VARCHAR(64),
    user_agent   TEXT
);

-- Fast lookup by token
CREATE INDEX idx_magic_link_token_token
    ON magic_link_token (token);

-- Helpful for cleanup jobs (delete expired / used tokens)
CREATE INDEX idx_magic_link_token_expires_at
    ON magic_link_token (expires_at);

CREATE INDEX idx_magic_link_token_used_at
    ON magic_link_token (used_at);

-- To quickly see tokens for a given user (debug/audit)
CREATE INDEX idx_magic_link_token_user_id
    ON magic_link_token (user_id);
