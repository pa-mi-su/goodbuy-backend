-- V18__create_user_sessions.sql
CREATE TABLE user_session (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id      UUID NOT NULL REFERENCES app_user(id) ON DELETE CASCADE,

    -- opaque session token returned to iOS and sent as X-Session-Token
    token        VARCHAR(255) NOT NULL UNIQUE,

    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at   TIMESTAMPTZ NOT NULL,
    revoked_at   TIMESTAMPTZ,

    ip_address   VARCHAR(64),
    user_agent   TEXT
);

CREATE INDEX idx_user_session_token ON user_session(token);
CREATE INDEX idx_user_session_user_id ON user_session(user_id);
CREATE INDEX idx_user_session_expires_at ON user_session(expires_at);
CREATE INDEX idx_user_session_revoked_at ON user_session(revoked_at);
